package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;
import org.soutalab.celtium.vulkan.RenderObjectIds;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libffi.FFICIF;
import org.lwjgl.system.libffi.FFIType;
import org.lwjgl.system.libffi.LibFFI;

import static org.lwjgl.system.APIUtil.apiCreateCIF;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memGetLong;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.memPutLong;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_double;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_float;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_pointer;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint32;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint64;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_uint32;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_void;

/**
 * Raw-GL compatibility adapter, answered without a GL context or GL driver drawing.
 * <p>
 * {@code GL.create(provider)} routes supported GL entry points to recorded state and native resources. Minecraft/Forge
 * APIs are being migrated to native owners; residual vanilla arrivals here are migration debt, not proof of a native
 * frontend. The adapter emulates supported GL semantics; missing/refused/no-op calls do not establish a correct image.
 * The compatibility records include:
 * <ul>
 *   <li>Object names - {@code glGenTextures}, {@code glGenFramebuffers} and kin return real, distinct ids, because
 *   texture lookup, framebuffer identity and every cached descriptor key on the names being unique.</li>
 *   <li>State that is queried - {@code glGetString}, {@code glGetError}, {@code glIsEnabled}, {@code glGetInteger*}
 *   answer plausibly from a recorded state so capability detection and error checks behave.</li>
 *   <li>Uploaded pixels - {@code glTexImage1D/2D/3D} and {@code glTexSubImage2D} are converted to RGBA8 and kept in
 *   stub-owned storage, because GL-less mode has no driver to read a texture back from and the Vulkan mirrors have to
 *   be built from the bytes the game handed over. {@link #textureUploadRgba8} is that record and
 *   {@link #textureCaptureSummary} is its census; an upload whose format is not understood is counted and dropped
 *   rather than stored wrong.</li>
 *   <li>Supported draws/state feed the native backend. Unsupported calls are counted or refused; they can lose visible
 *   work and must not be described as a successful fallback.</li>
 * </ul>
 */
public final class CeltiumGlStub implements FunctionProvider {

    /** C return types. Java-side only - the trampoline marshals them into the libffi result slot. */
    static final byte R_VOID = 0;
    static final byte R_INT = 1;
    static final byte R_LONG = 2;
    static final byte R_PTR = 3;

    /**
     * Argument types as they arrive at the trampoline: every arg slot is a pointer to the value. Package-private
     * rather than private (the SEM_* ids below have the same shape) because {@link GlTrampolines} reads the same
     * encoding when it builds a row's CIF and picks its upcall shape, and {@link GlFunctionTable} reads the same
     * ids when it registers a row.
     */
    static final byte A_INT = 11;
    static final byte A_LONG = 12;
    static final byte A_PTR = 13;
    static final byte A_FLOAT = 14;
    static final byte A_DOUBLE = 15;
    static final byte A_UINT = 16;

    // Semantics ids - what dispatch() does with the call. The enum keeps the table lines short.
    static final int SEM_NOOP = 0;            // record only - state the Vulkan side owns or nobody reads
    static final int SEM_GEN = 1;             // object-name allocation: returns the next id
    static final int SEM_GET_ERROR = 2;       // always GL_NO_ERROR - nothing the stub does can raise one
    static final int SEM_GET_STRING = 3;      // arg0 pname -> interned ascii
    static final int SEM_IS_ENABLED = 4;      // arg0 cap -> recorded enable bit
    static final int SEM_ENABLE = 5;          // arg0 cap -> set enable bit
    static final int SEM_DISABLE = 6;         // arg0 cap -> clear enable bit
    static final int SEM_GET_INTEGERV = 7;    // arg0 pname, arg1 out[] -> plausible ints
    static final int SEM_GET_FLOATV = 8;      // arg0 pname, arg1 out[] -> plausible floats
    static final int SEM_GET_BOOLEANV = 9;    // arg0 pname, arg1 out[] -> plausible bytes
    static final int SEM_IS_OBJECT = 10;      // arg0 id -> whether the table generated it
    static final int SEM_DELETE = 11;         // id list -> retire names (reissue is the same counter-free)
    static final int SEM_GET_STRINGI = 12;    // pname, index -> extension string
    static final int SEM_GET_INTEGER64V = 13; // pname, out64[] -> plausible longs
    private static final int SEM_ROUTE = 14;          // pixel/semantic routing - handled in route()
    static final int SEM_ZERO = 15;           // int return: 0 - valid-but-empty answers (uniform loc etc)
    static final int SEM_FBO_COMPLETE = 16;   // int return: GL_FRAMEBUFFER_COMPLETE
    static final int SEM_SIGNALED = 17;       // int return: GL_ALREADY_SIGNALED
    static final int SEM_MAP = 18;            // ptr return: the named buffer's storage
    static final int SEM_UNMAP = 19;          // byte return: true, buffer contents already live
    static final int SEM_BUFFER_DATA = 20;    // (target,size,data,usage) -> size bytes of storage
    static final int SEM_STATUS = 21;         // (obj,pname,out[]) -> successful-answer queries
    static final int SEM_INFO_LOG = 22;       // (obj,bufSize,len*,log) -> empty string
    static final int SEM_BIND_BUFFER = 23;    // (target,name) -> binding record for map/data
    static final int SEM_BIND_TEXTURE = 24;   // (target,name) -> texture binding record
    static final int SEM_ACTIVE_TEXTURE = 25; // (unit) -> texture unit selector record
    static final int SEM_TEXIMAGE = 26;       // (target,level,internal,w,h,...) -> size record per bound
    static final int SEM_VIEWPORT = 27;       // (x,y,w,h) -> viewport record feeding GL_VIEWPORT
    static final int SEM_FOG_I = 71;
    static final int SEM_FOG_F = 72;
    static final int SEM_FOG_FV = 73;
    static final int SEM_FOG_IV = 74;
    static final int SEM_TEX_ENV_I = 47;      // (target,pname,param) -> texture-env int, per active unit
    static final int SEM_TEX_ENV_F = 48;      // (target,pname,param) -> texture-env float
    static final int SEM_TEX_ENV_FV = 49;     // (target,pname,values) -> texture-env float array
    static final int SEM_GET_TEX_ENV_I = 50;  // (target,pname,out) -> the recorded int
    static final int SEM_GET_TEX_ENV_FV = 51; // (target,pname,out) -> the recorded floats
    static final int SEM_COPY_TEX_IMAGE = 52;    // copy from the bound read framebuffer into a texture level
    static final int SEM_COPY_TEX_SUB_IMAGE = 53; // the same, into a sub-rectangle
    static final int SEM_BLIT_FRAMEBUFFER = 78;  // framebuffer-to-framebuffer blit (the depth bit is served)
    static final int SEM_COPY_IMAGE_SUB_DATA = 79; // (srcName,srcTarget,srcLevel,srcX,srcY,srcZ,dstName,dstTarget,dstLevel,dstX,dstY,dstZ,w,h,d) -> captured level to captured level
    static final int SEM_TEX_PARAM_I = 54;     // (target,pname,param) -> texture parameter of the bound texture
    static final int SEM_TEX_PARAM_IV = 55;    // (target,pname,values) -> the same, from an array
    static final int SEM_TEX_PARAM_I_DSA = 56; // (texture,pname,param) -> the same, texture named directly
    static final int SEM_TEX_PARAM_IV_DSA = 57; // (texture,pname,values) -> the same, from an array
    static final int SEM_MULT_MATRIX = 58;     // (matrix) -> multiply the active matrix of the mirror
    static final int SEM_MATRIX_MODE = 59;     // (mode) -> which stack glMultMatrix* multiplies
    static final int SEM_BIND_FRAMEBUFFER = 28; // (target,name) -> framebuffer binding record
    static final int SEM_CLEAR = 29;          // (mask) -> pending clear of the owned frame image
    static final int SEM_CLEAR_COLOR = 30;    // (r,g,b,a) -> the colour glClear will use
    static final int SEM_CLEAR_DEPTH = 31;    // (d) -> the depth glClear will use
    static final int SEM_BUFFER_SUBDATA = 32; // (target,offset,size,data) -> into the recorded storage
    static final int SEM_LINE_WIDTH = 33;      // (w) -> recorded raster line width
    static final int SEM_USE_PROGRAM = 34;     // (program) -> recorded current program
    static final int SEM_NEW_LIST = 35;        // (list,mode) -> the list being compiled
    static final int SEM_END_LIST = 36;        // () -> compilation finished
    static final int SEM_CALL_LIST = 37;       // (list) -> recorded for the display-list census
    static final int SEM_BIND_VERTEX_ARRAY = 38; // (vao) -> recorded vertex array binding
    static final int SEM_TEXIMAGE_PIXELS = 39; // (target,level,internal,w,h,border,fmt,type,pixels) -> extent + RGBA8
    static final int SEM_TEXSUBIMAGE = 40;     // (target,level,x,y,w,h,fmt,type,pixels) -> patch into a captured level
    static final int SEM_COMPRESSED_TEXIMAGE = 41; // (target,level,internal,w,h,border,size,data) -> extent only
    static final int SEM_PIXEL_STORE = 42;     // (pname,value) -> the unpack state upload conversion needs
    static final int SEM_VERTEX_POINTER = 43;  // (size,type,stride,offset) -> how to read the bound array buffer
    static final int SEM_DRAW_ARRAYS = 44;     // (mode,first,count) -> one array-buffer draw, drawn natively
    private static final int SEM_COMPILED_ARRAYS = -2;
    private static final Fn COMPILED_ARRAYS = new Fn("compiledClientArrays", R_VOID, new byte[0], SEM_COMPILED_ARRAYS);
    // Step 1 of the loading-screen unit: glBegin/glEnd leave the generic no-op table so the *site* that opens a
    // legacy immediate-mode run can be proven rather than counted. Forge's SplashProgress draws the loading screen
    // with glBegin(GL_QUADS) (SplashProgress.java:294/:353/:416) straight through GL11, which under the GL-less
    // window reached SEM_NOOP and drew nothing; the no-op census showed glVertex3f=152460/glBegin=38115/glEnd=38115
    // but a count is not a site. This step only names the callers; the vertex recording and the submit are the next
    // two steps, each landing on its own.
    static final int SEM_BEGIN = 68;           // (mode) -> the site that opens an immediate-mode run
    static final int SEM_END = 69;             // () -> the site that closes it
    static final int SEM_READ_PIXELS = 70;     // (x,y,w,h,format,type,pixels) -> read the bound read framebuffer
    static final int SEM_SCISSOR = 45;         // (x,y,w,h) -> the game's clip rectangle, for the group's scissor
    static final int SEM_CULL_FACE = 60;       // arg0 = the face GL will cull
    static final int SEM_FRONT_FACE = 61;      // arg0 = the winding GL calls front
    static final int SEM_POLYGON_OFFSET = 62;  // (factor, units) as float bits -> the depth bias GL wants
    static final int SEM_COLOR_MASK = 63;      // (r,g,b,a) -> which channels the draw may write (BH-10)
    static final int SEM_BLEND_FUNC = 64;      // (src,dst) -> all four GL blend factors, as glBlendFunc does
    static final int SEM_BLEND_FUNC_SEPARATE = 65; // (srcRgb,dstRgb,srcAlpha,dstAlpha) -> the four factors
    static final int SEM_BLEND_EQUATION = 66;  // (mode) -> both blend equations
    static final int SEM_BLEND_EQUATION_SEPARATE = 67; // (rgbMode, alphaMode) -> the two equations
    static final int SEM_LIST_BASE = 75;      // (base) -> the offset glCallLists adds to every name
    static final int SEM_CALL_LISTS = 76;     // (count, type, address) -> execute a run of display lists
    static final int SEM_BIND_IMAGE_TEXTURE = 77; // full GL image-unit tuple -> native ledger

    /** One function-table row: name, return type, arg types, semantics. */
    static final class Fn {
        final String name;
        final byte ret;
        final byte[] args;
        final int semantics;
        /**
         * Whether this is one of the DSA {@code *Named*} entry points, whose first argument is the object name rather
         * than a target whose binding is used.
         * <p>
         * Decided once, here: the buffer-data, sub-data and map entry points used to ask
         * {@code fn.name.contains("NamedBuffer")} on every call, and vanilla uploads through them for every entity and
         * tile entity batch - the profile put {@code String.contains} at 2.1% of the warm window's samples inside
         * block-entity rendering.
         */
        final boolean namedObject;
        /** Per-function arrival count, under {@link #FN_CENSUS}: the distribution of the trampoline's producers. */
        long calls;

        Fn(String name, byte ret, byte[] args, int semantics) {
            this.name = name;
            this.ret = ret;
            this.args = args;
            this.semantics = semantics;
            this.namedObject = name.contains("Named");
        }
    }

    /** Shared by every unresolved name: the dispatch path must not allocate an empty array per unknown symbol. */
    private static final byte[] EMPTY_ARGS = new byte[0];
    private static final List<Fn> TABLE = new ArrayList<>();
    private static final Map<String, Integer> IDS = new HashMap<>();

    static Fn fn(String name, byte ret, int semantics, byte... args) {
        Fn fn = new Fn(name, ret, args, semantics);
        TABLE.add(fn);
        return fn;
    }

    static {
        GlFunctionTable.registerAll();
        for (int i = 0; i < TABLE.size(); i++) {
            IDS.putIfAbsent(TABLE.get(i).name, Integer.valueOf(i));
        }
    }

    /* The registration methods that used to sit here (registerNamesAndQueries and its three
       siblings) live in GlFunctionTable, which the static block above calls. */

    // ---- runtime state ---------------------------------------------------------------

    /**
     * The upcall machinery - FFM upcalls, libffi trampolines and their per-name caches: {@link GlTrampolines},
     * fed with this stub's own dispatch entries as method references.
     */
    private final GlTrampolines glTrampolines = new GlTrampolines(this::dispatch, this::legacyDispatch);
    // GAP-ST-10 rewired: the live-name/sync ledger (and the sync id source) is GlsmNameLedger.
    /**
     * The GL state tables are open-addressed primitive maps, not ConcurrentHashMaps: every read and write happens on
     * the render thread by GL contract (the stub's callbacks are invoked by the backend there, never by a worker),
     * so the concurrency the CHMs bought was unused - while their per-call cost was real: a {@code Long.valueOf} box
     * plus the hash probe on every bind and every texture-parameter lookup, ~4.6% of the unbounded leg's warm
     * samples between {@code ConcurrentHashMap.get}/{@code putVal} and {@code Integer.valueOf}.
     * <p>
     * The thread policy for these tables (audit-gl-deep G2, remainder): they are confined to whichever thread is
     * inside dispatch. Arrivals are serialized at the {@code dispatchArgs} funnel by the
     * {@link DispatchThreadGate}: a call that arrives while another thread is mid-dispatch is refused and counted
     * ({@link #celtium$noteForeignDispatch}) rather than raced against the tables, because an open-addressed map
     * under a concurrent write can lose entries or loop. Sequential arrivals from other threads are served one at
     * a time - they are a measured reality (a mod querying {@code glGetString} during construction, GL calls out
     * of a {@code preInit}, class-loading threads), and they touch the tables only while no dispatch is in flight.
     * The direct {@code celtium$*} statics are part of the same confined surface: their callers are the same
     * game/mod code under the same contract, plus the render-thread backend, so they are covered by this policy
     * statement rather than by per-method guards.
     */
    // The two hottest maps the GL call path owned were hash lookups: every glEnable/glDisable/glIsEnabled took an
    // Int2Object get/put through a Boolean box, and every buffer bind did the same through Int2Int. Both key spaces
    // are capability/target constants - dense, small, and bounded well under 0x10000 - so they are flat arrays now:
    // one indexed load or store per call, no hashing, no boxing, no tree-node buckets (the steady-state JFR showed
    // HashMap$TreeNode.getTreeNode and Long2ObjectOpenHashMap.get among the top frames). An index of 0 is "never
    // set", which is the same answer the maps gave for absent keys: GL treats an unqueried capability as disabled
    // and an unbound target as name 0.
    private static final int FLAT_MAP_SIZE = 0x10000;
    // GAP-ST-1 rewired: the enable/observed record is owned by GlsmEnableState (singleton). A false value there
    // may be an observed disable or simply an untouched provider default; native replacements can bypass
    // callbacks, so the latter must never erase state captured through Minecraft's API.
    // GAP-ST-2 rewired: buffer bindings and storage are owned by GlsmBufferState.
    /** Bound texture name per (active unit, target). The game uses one unit; the key keeps the option open. A read
     * of an unbound (unit,target) returns -1 - never a valid GL name. */
    final it.unimi.dsi.fastutil.longs.Long2IntMap textureBindings =
            new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    /**
     * First caller each texture name was bound through, either route - a bound-but-never-uploaded name cannot have
     * its producer reconstructed from the upload records (there are none), which is exactly the state the "no mirror
     * for this GL id" refusal reports. Bounded so a mod that churns names cannot grow it; texture name 506 in the
     * E2E pack was bound and never uploaded, and without this there was nothing to name the caller.
     */
    private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<String> firstBindSites =
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<String>();
    /**
     * {@code -Dceltium.debug.bindSites=true}: remember who first bound each texture name.
     * <p>
     * Only the "no mirror" refusal's message reads it, but it used to run on every texture bind. A bound-texture cache
     * already answers the question the renderer needs, and the diagnostic's map is one of the colliding-bin lookups that
     * a no-validation profile put at three percent of the client thread, so it is off unless asked for.
     */
    private static final boolean BIND_SITE_DIAGNOSTICS = Boolean.getBoolean("celtium.debug.bindSites");
    private static final int MAX_BIND_SITES = 4096;
    /** Recorded image extents: key {@code (textureName << 16) | level}, proxy targets key on a name of -target. */
    final it.unimi.dsi.fastutil.longs.Long2ObjectMap<int[]> texLevels =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    /**
     * {@code -Dceltium.vulkan.targetFormatCensus=true}: the stub side of the format census (QUEUE 405, S5) -
     * the same switch the backend's decision census reads, so one leg flag turns the whole census on. Off,
     * the distribution below is never touched.
     */
    private static final boolean TARGET_FORMAT_CENSUS = Boolean.getBoolean("celtium.vulkan.targetFormatCensus");
    /** Distinct internal formats the distribution tracks; past the cap a format is not recorded. */
    private static final int MAX_TARGET_FORMATS = 32;
    /**
     * How often each colour internal format was recorded into {@link #texLevels} at level 0, under
     * {@link #TARGET_FORMAT_CENSUS}: the distribution that answers "did this fixture's mods actually ask
     * for 16F" at the GL surface, before any pair decision is made about it. Depth formats are not
     * counted - a frame's depth is {@code VulkanDepthFormat}'s answer, not a colour request.
     */
    private final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap targetFormatRequests =
            new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
    /**
     * Captured uploads in RGBA8, keyed like {@link #texLevels}. A full {@code glTexImage*} publishes a new record, so
     * a reader sees the old image or the new one and never a half-written one; {@code glTexSubImage2D} patches rows
     * of the record that is already there.
     */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<GlTextureCaptures.TexCapture> texCaptures =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    /**
     * The write serial each level's capture carried when {@link #textureUploadRgba8} last handed it to a reader,
     * keyed like {@link #texCaptures}. A level whose entry still equals its capture's serial at release time is
     * one a mirror has already taken in full and nothing has rewritten since - the pair of facts
     * {@link #releaseSettledTextureCaptures} needs before it may drop the stub's own copy. Entries are removed
     * with their texture and with a released capture, so the map stays the size of the supplied set. Every
     * access holds the map's own monitor: the recording write happens inside {@link #textureUploadRgba8}, which
     * is documented safe from any thread, while the release reads and removes on the render thread.
     */
    private final it.unimi.dsi.fastutil.longs.Long2LongMap suppliedSerials =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    /**
     * Patches a level could not hold at full extent - the budget above - stay in a per-level backlog, which the
     * mirror path replays as region writes. This is how a big atlas fills at all: its sprite uploads are raw
     * {@code glTexSubImage2D} calls that carry no announcement (measured: 37,713 captured patches vs ~1,182
     * announced fills in one E2E run), so without the backlog the mirror covered only the announced fraction.
     * Bounded because the texture may never be mirrored; the atlas's whole stitch burst measured ~143 MB.
     */
    // Primitive-keyed: textureUploadSerial reads it on every offered batch, and a boxed Long key was an allocation and
    // a Long.equals per offer.
    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<java.util.List<GlTextureCaptures.TexCapture>> texPatchBacklog =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<java.util.List<GlTextureCaptures.TexCapture>>();

    /**
     * The texture-pixel captures themselves - the filed records, the patch backlog's bookkeeping, the
     * conversion and the capture census - live in {@link GlTextureCaptures} (giant-split seam 7, QUEUE 688),
     * constructed over the three stores above, which stay fields of this stub: {@code forgetTexture} and
     * the copy-image path read them beside the captures' own state, and the cleanup test reads
     * {@code texCaptures} by name. The write serial and the census counters moved with the captures.
     */
    final GlTextureCaptures textureCaptures = new GlTextureCaptures(this.texCaptures, this.texPatchBacklog,
            this.suppliedSerials);
    /**
     * The unpack state {@code glPixelStorei} set. Conversion reads rows where GL would read them: assuming tight rows
     * while the game asked for a row length or a row skip would store a sheared image instead of a missing one.
     */
    // GAP-ST-2 (S-5): pixel-store state is also GlsmBufferState's.
    /* The per-name logical sizes that used to sit here live in BufferStorageRecord, which operates on the store below. */
    long getStringAddress;
    ByteBuffer getStringBuffer;
    // GAP-ST-5 rewired: the viewport's raw rectangle and packed form are owned by GlsmViewportState.
    /** GL_DRAW_FRAMEBUFFER / GL_READ_FRAMEBUFFER bindings; GL_FRAMEBUFFER sets both. */
    volatile int drawFbo, readFbo;
    /** The colour and depth glClear will use, and the buffers it was asked to clear and nobody consumed yet. */
    // GAP-ST-4 rewired: current clear values, the pending latch and the issue-time snapshots
    // are owned by GlsmClearState.

    /** Clears seen for the framebuffer the game calls its main one, and for every other target. */
    /** Bounded caller log for main-framebuffer clears: which caller clears it, and how often. */
    private static final boolean CLEAR_CALLERS =
            Boolean.parseBoolean(System.getProperty("celtium.vulkan.clearCallers", "false"));
    private static final int CLEAR_CALLER_LINES = 16;
    private int mainClearCallerLogs;

    /** {@return the main/foreign clear census and the last main clear colour}, for the verification line. */
    public static String clearCensus() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "main=none foreign=none colour=none";
        }
        GlsmClearState clear = GlsmClearState.current();
        return "main=" + clear.mainClears() + "/" + clear.lastMainRed() + "," + clear.lastMainGreen() + ","
                + clear.lastMainBlue() + " foreign=" + clear.foreignClears() + "/" + clear.lastForeignRed() + ","
                + clear.lastForeignGreen() + "," + clear.lastForeignBlue();
    }

    /**
     * {@return the recorded colour internal formats and their counts} for the format census (QUEUE 405,
     * S5), as {@code 0x<gl>=<count>} pairs in GLenum order: {@code "off"} when the census switch is off
     * (a distribution that was never counted must not read as an empty one) and {@code "none"} when it
     * is on but no colour format was recorded.
     */
    public static String targetFormatDistribution() {
        if (!TARGET_FORMAT_CENSUS) {
            return "off";
        }
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "none";
        }
        synchronized (stub.targetFormatRequests) {
            if (stub.targetFormatRequests.isEmpty()) {
                return "none";
            }
            int[] formats = stub.targetFormatRequests.keySet().toIntArray();
            java.util.Arrays.sort(formats);
            StringBuilder out = new StringBuilder();
            for (int format : formats) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append("0x").append(Integer.toHexString(format)).append('=')
                        .append(stub.targetFormatRequests.get(format));
            }
            return out.toString();
        }
    }
    /** Set by the backend after each bind, so a clear is classified by routing rather than by a raw GL name. */
    private volatile boolean drawFramebufferIsMain = true;

    /** Fixed-pipeline state the getters answer from, at the values a fresh GL context starts them at. */
    // GAP-ST-9 rewired: the immediate mode's current colour is GlsmImmediateState's.
    /** Scratch the clear-value query fills through {@link #mainClearValues(float[])}. */
    final float[] clearValueScratch = new float[4];

    /** GL's initial light-model ambient, answered when no backend mirror exists. */
    final float[] lightAmbientDefault = {0.2f, 0.2f, 0.2f, 1.0f};
    /** The last {@code glPointSize} request. Points are not rasterized, so this exists for the query only. */
    // GAP-ST-9: line width and point size are GlsmImmediateState's too.
    /** The program glUseProgram bound, the display list being compiled, and the bound vertex array. */
    volatile int boundVertexArray;
    // GAP-PROG-1 rewired: the current program is GlsmShaderLedger's (SEM_USE_PROGRAM writes it there).
    // GAP-ST-7 rewired: compilingList/compilingMode, lastCalledList, executingList and listBase
    // are GlsmDisplayListLedger's, as are the compiled-list/geometry/payload maps and counters below.

    /**
     * The programs a draw has been decoded under, so the first draw of each is reported once.
     * <p>
     * A mod's post-processing (GTCEu's bloom composite, the outline chain) draws full-screen quads with its own
     * {@code ShaderProgram} bound. The immediate path refuses a batch while a custom program is bound, and the refusal
     * is logged from the backend - but only for a batch that reaches it. A draw that the decode refuses first, or one
     * whose program is already 0 by the time it is drawn, leaves no line at all, and "the composite never drew" and
     * "the composite drew and was refused" are different findings. This census is the one that tells them apart.
     */
    private final it.unimi.dsi.fastutil.ints.IntOpenHashSet celtium$programDraws =
            new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    /**
     * The programs {@code glUseProgram} has bound, so the first bind of each is reported once with its call site.
     * <p>
     * The draw census above answers "which program was a draw decoded under"; this one answers "was a program bound at
     * all, and by whom". They are different findings and the GTCEu bloom investigation needed both: the draw census was
     * empty because the bind itself never reached the stub.
     */
    final it.unimi.dsi.fastutil.ints.IntOpenHashSet celtium$programBinds =
            new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    /** The last name {@code GlStateManager.bindTexture} named. See {@link #celtium$noteBoundTexture}. */
    private volatile int lastBoundTexture = -1;
    /** {@code GL_COMPILE_AND_EXECUTE}: the commands are recorded and also executed as they arrive}. */
    private static final int GL_COMPILE_AND_EXECUTE = 0x1301;

    /**
     * {@return whether a command is executed rather than compiled into a display list}.
     * <p>
     * The specification lists these: object-name allocation and deletion, the queries, the client-pointer mapping
     * commands and the status/info-log reads. They are answered immediately even while {@code glNewList} is open,
     * because the caller uses the answer at that point in the stream (#393).
     */
    static boolean notCompiledIntoDisplayList(int semantics) {
        switch (semantics) {
            case SEM_VERTEX_POINTER:
            case SEM_GEN:
            case SEM_DELETE:
            case SEM_GET_ERROR:
            case SEM_GET_STRING:
            case SEM_GET_STRINGI:
            case SEM_GET_INTEGERV:
            case SEM_GET_FLOATV:
            case SEM_GET_BOOLEANV:
            case SEM_GET_INTEGER64V:
            case SEM_GET_TEX_ENV_I:
            case SEM_GET_TEX_ENV_FV:
            case SEM_IS_ENABLED:
            case SEM_IS_OBJECT:
            case SEM_MAP:
            case SEM_UNMAP:
            case SEM_STATUS:
            case SEM_INFO_LOG:
            case SEM_FBO_COMPLETE:
            case SEM_SIGNALED:
                return true;
            default:
                return false;
        }
    }

    /**
     * Copies a compiled command's pointer payload into memory this list owns, and points the recorded argument at it.
     * <p>
     * Only the commands whose payload a replay would otherwise re-read from the caller's buffer are covered, and only
     * when the size is known: an unknown format is left pointing at the caller's address and counted, which is the
     * previous behaviour and is visible rather than silent (#394).
     *
     * @param fn      the command being compiled
     * @param args    the argument copy that will be stored; the pointer slot is rewritten here
     * @return the owned buffer, or null when the command carries no payload this class can size
     */
    private java.nio.ByteBuffer captureListPayload(Fn fn, long[] args) {
        int pointerIndex;
        long bytes;
        switch (fn.semantics) {
            case SEM_TEXIMAGE_PIXELS: {
                // (target, level, internal, w, h, border, fmt, type, pixels)
                if (args.length < 9 || args[8] == 0L) {
                    return null;
                }
                int width = (int) args[3];
                int height = (int) args[4];
                int rowBytes = uploadRowBytes((int) args[6], (int) args[7], width);
                if (rowBytes < 0 || width <= 0 || height <= 0) {
                    this.listPayloadsUnsized++;
                    return null;
                }
                pointerIndex = 8;
                bytes = texUploadSpanBytes(rowBytes, width, height);
                if (bytes < 0L) {
                    this.listPayloadsUnsized++;
                    return null;
                }
                break;
            }
            case SEM_TEXSUBIMAGE: {
                // (target, level, x, y, w, h, fmt, type, pixels)
                if (args.length < 9 || args[8] == 0L) {
                    return null;
                }
                int width = (int) args[4];
                int height = (int) args[5];
                int rowBytes = uploadRowBytes((int) args[6], (int) args[7], width);
                if (rowBytes < 0 || width <= 0 || height <= 0) {
                    this.listPayloadsUnsized++;
                    return null;
                }
                pointerIndex = 8;
                bytes = texUploadSpanBytes(rowBytes, width, height);
                if (bytes < 0L) {
                    this.listPayloadsUnsized++;
                    return null;
                }
                break;
            }
            case SEM_BUFFER_DATA: {
                // (target, size, data, usage)
                if (args.length < 4 || args[2] == 0L) {
                    return null;
                }
                pointerIndex = 2;
                bytes = args[1];
                break;
            }
            case SEM_BUFFER_SUBDATA: {
                // (target, offset, size, data)
                if (args.length < 4 || args[3] == 0L) {
                    return null;
                }
                pointerIndex = 3;
                bytes = args[2];
                break;
            }
            case SEM_MULT_MATRIX: {
                if (args.length < 1 || args[0] == 0L) {
                    return null;
                }
                pointerIndex = 0;
                bytes = 64L;
                break;
            }
            case SEM_TEX_ENV_FV:
            case SEM_TEX_PARAM_IV: {
                // (target, pname, values): the widest form the fixed pipeline takes is a four-component parameter.
                if (args.length < 3 || args[2] == 0L) {
                    return null;
                }
                pointerIndex = 2;
                bytes = 16L;
                break;
            }
            default:
                return null;
        }
        if (bytes <= 0L || bytes > MAX_LIST_PAYLOAD_BYTES) {
            this.listPayloadsUnsized++;
            return null;
        }
        int length = (int) bytes;
        java.nio.ByteBuffer owned = org.lwjgl.system.MemoryUtil.memAlloc(length);
        org.lwjgl.system.MemoryUtil.memCopy(args[pointerIndex], org.lwjgl.system.MemoryUtil.memAddress(owned),
                (long) length);
        args[pointerIndex] = org.lwjgl.system.MemoryUtil.memAddress(owned);
        this.listPayloadsOwned++;
        return owned;
    }

    /**
     * {@return the bytes a texture upload spans in client memory under the current unpack state, or -1 when
     * that state lays the rows out in a way this capture cannot mirror}.
     *
     * <p>The conversion that later reads the captured bytes pads each row to {@code GL_UNPACK_ALIGNMENT} and
     * reads {@code (height - 1)} padded rows plus the last row unpadded, and it refuses a row length or a
     * skip outright. Sizing the capture by a fixed alignment of 4 instead under-read the caller's memory at
     * alignment 8 - and the conversion then read past the owned buffer at replay - and misaligned every row
     * when a row length was in play, so the capture takes the same span and the same refusals as the
     * conversion.
     */
    long texUploadSpanBytes(int rowBytes, int width, int height) {
        if ((GlsmBufferState.current().unpackRowLength() != 0 && GlsmBufferState.current().unpackRowLength() != width)
                || GlsmBufferState.current().unpackSkipPixels() != 0 || GlsmBufferState.current().unpackSkipRows() != 0) {
            return -1L;
        }
        return (long) aligned(rowBytes, GlsmBufferState.current().unpackAlignment()) * (height - 1) + rowBytes;
    }

    /**
     * {@return the bytes in one row of an upload, or -1 when the format/type pair is not one this class sizes}.
     *
     * @param format the GL format of the client data
     * @param type   the GL type of the client data
     * @param width  the row's pixel count
     */
    private static int uploadRowBytes(int format, int type, int width) {
        if (width <= 0) {
            return -1;
        }
        int components;
        switch (format) {
            case GL_RGBA:
            case GL_BGRA:
                components = 4;
                break;
            case GL_RGB:
            case GL_BGR_VALUE:
                components = 3;
                break;
            case GL_LUMINANCE_ALPHA_VALUE:
                components = 2;
                break;
            case GL_LUMINANCE_VALUE:
            case GL_ALPHA_VALUE:
            case GL_DEPTH_COMPONENT_VALUE:
                components = 1;
                break;
            default:
                return -1;
        }
        int bytesPerComponent;
        switch (type) {
            case GL_UNSIGNED_BYTE:
            case GL_BYTE_VALUE:
                bytesPerComponent = 1;
                break;
            case GL_UNSIGNED_SHORT_VALUE:
            case GL_SHORT_VALUE:
                bytesPerComponent = 2;
                break;
            case GL_UNSIGNED_INT_VALUE:
            case GL_INT_VALUE:
            case GL_FLOAT:
                bytesPerComponent = 4;
                break;
            default:
                return -1;
        }
        return width * components * bytesPerComponent;
    }

    /** Releases a list's owned payloads; called when the list is replaced or deleted (#394). */
    private List<NativeGeometryBuffer> openListGeometry;

    /**
     * {@return the vertex bytes the compiled lists hold}, for the census line.
     * <p>
     * A list's geometry is a CPU copy owned by the stub until the name is replaced or deleted, so the total is what
     * says whether recording every list is bounded. It is summed on demand: the census prints a handful of times per
     * run, and the map holds only the lists that captured a draw.
     */
    long heldListGeometryBytes() {
        long bytes = 0L;
        for (List<?> rawBuffers : GlsmDisplayListLedger.current().geometryLists()) {
            @SuppressWarnings("unchecked")
            List<NativeGeometryBuffer> buffers = (List<NativeGeometryBuffer>) rawBuffers;
            for (int i = 0; i < buffers.size(); i++) {
                NativeGeometryBuffer buffer = buffers.get(i);
                bytes += (long) buffer.vertexCount() * buffer.stride();
            }
        }
        return bytes;
    }

    private void releaseListPayloads(Integer list) {
        List<NativeGeometryBuffer> geometry = GlsmDisplayListLedger.current().removeGeometry(list.intValue());
        if (geometry != null) {
            for (NativeGeometryBuffer buffer : geometry) buffer.close();
            this.listPayloadsReleased += geometry.size();
        }
        // The compiled legacy runs' byte allowance goes back with the list, before the buffers are freed: the copies
        // are in the same payload list, so the subtraction and the release belong to the same event.
        long legacyBytes = this.legacyListRunBytesByList.remove(list.intValue());
        if (legacyBytes > 0L) {
            this.legacyListRunBytes -= legacyBytes;
        }
        List<java.nio.ByteBuffer> payloads = GlsmDisplayListLedger.current().removePayloads(list.intValue());
        if (payloads == null) {
            return;
        }
        for (int i = 0; i < payloads.size(); i++) {
            org.lwjgl.system.MemoryUtil.memFree(payloads.get(i));
        }
        this.listPayloadsReleased += payloads.size();
    }

    /**
     * The list {@code glCallList} is replaying right now, 0 outside a replay.
     *
     * <p>Separate from {@link #lastCalledList}, which stays at the last name for diagnostics. This one is the
     * answer to "what is being drawn", which {@code GL_LIST_INDEX} cannot give: the query names the list being
     * compiled and stays 0 while a list is executed.
     */
    /**
     * The display lists this stub has compiled, keyed by list name.
     *
     * <p>A display list is not a draw: {@code glNewList(name, GL_COMPILE)} records the calls that follow and
     * {@code glCallList(name)} replays them, and the spec is explicit that under GL_COMPILE the recorded commands are
     * "merely compiled". Minecraft's model rendering depends on exactly that split - {@code ModelRenderer.render}
     * compiles once through {@code compileDisplayList} and then draws with {@code glCallList(displayList)} - so a
     * stub that answers glCallList with a no-op leaves every model in the game undrawn. {@code RenderGlobal}'s sky,
     * stars and horizon plane are compiled the same way ({@code glSkyList}, {@code starGLCallList}, {@code glSkyList2}).
     *
     * <p>{@link #openList} is the list currently being recorded, or null when nothing is being compiled. The two are
     * kept apart from {@link #compilingList} because that field already existed as the name only, and because a name
     * of zero is not a list: recording under it would file calls where no glCallList can reach them.
     */
    private volatile List<Object[]> openList;

    /**
     * The pointer payloads of the list being compiled, owned here instead of by the caller (#394).
     * <p>
     * A command that reads through a pointer - {@code glTexImage2D}'s pixels, {@code glBufferData}'s bytes,
     * {@code glMultMatrixf}'s sixteen floats - has to be given what the specification calls a copy of the data at
     * compile time: the caller is entitled to reuse or free its buffer after {@code glEndList}, and a replay that read
     * the original address would sample whatever is there then, or freed memory. These buffers live with the list and
     * are released when the list is replaced or deleted.
     */
    private List<java.nio.ByteBuffer> openListPayloads;

    /** Compiled commands whose pointer payload was copied into list-owned memory. */
    private long listPayloadsOwned;

    /** Compiled commands whose pointer payload could not be sized and was left pointing at the caller's buffer. */
    private long listPayloadsUnsized;

    /** Payload buffers released with their list. */
    private long listPayloadsReleased;

    /** Display-list names retired by glDeleteLists. */

    /** A payload larger than this is left alone: it is not a constant or an upload this class is meant to own. */
    private static final long MAX_LIST_PAYLOAD_BYTES = 64L * 1024L * 1024L;

    // The format/type values the payload sizing compares. GL_RGBA, GL_RGB, GL_UNSIGNED_BYTE, GL_BGRA and GL_FLOAT are
    // already declared by this class; the rest are added here so the sizing stays a pure function.
    private static final int GL_BYTE_VALUE = 0x1400;
    private static final int GL_SHORT_VALUE = 0x1402;
    private static final int GL_UNSIGNED_SHORT_VALUE = 0x1403;
    private static final int GL_INT_VALUE = 0x1404;
    private static final int GL_UNSIGNED_INT_VALUE = 0x1405;
    private static final int GL_DEPTH_COMPONENT_VALUE = 0x1902;
    private static final int GL_ALPHA_VALUE = 0x1906;
    private static final int GL_LUMINANCE_VALUE = 0x1909;
    private static final int GL_LUMINANCE_ALPHA_VALUE = 0x190A;
    private static final int GL_BGR_VALUE = 0x80E0;
    /**
     * Compiled lists by name. Single render thread, like every other GL object here: the concurrent map this used to be
     * never had a second caller, and the replay path (one glCallList per compiled model draw) paid a boxing key per
     * lookup for it.
     */
    /**
     * Replays, recorded calls and unresolved calls per display-list id, for the verification line.
     *
     * <p><b>Sparse, and that is the fix.</b> These were a fixed {@code int[512]} / {@code long[512]} pair indexed by the
     * list's GL name, and the census only recorded a replay whose id was below 512. Every measured run replays model
     * lists whose ids start above 110000 - {@code glGenLists} hands out names from a process-wide counter, and vanilla's
     * model renderers compile theirs after the GUI and the Tessellator have taken thousands - so the array could not
     * record the lists the census exists for. Measured on the calibrated world leg {@code r40b-gate}: the verification
     * line read {@code celtium/lists[none]} on all four of its prints while the same log holds
     * {@code CELTIUM_DISPLAY_LIST_ORIGIN list=110244 … 110338} for the chest, sign, banner, bed, skull and biped models.
     * A clean reading produced by a blind instrument is the one failure mode this project keeps finding.
     *
     * <p>A {@code Int2IntOpenHashMap} has no id ceiling, and the replay path already does one open-address lookup for the
     * list payload itself, so this is the same shape of cost as the array it replaces.
     */
    /**
     * The census maps, in a holder of their own.
     *
     * <p>Not a style choice: {@code CeltiumGlStub.<clinit>} is at 7936 of HotSpot's 8000-byte {@code HugeMethodLimit}
     * with these three allocations inline, and a method over the limit is never JIT-compiled - it runs interpreted for
     * the whole session. Round 20 hit the same wall with the drawing-name set and moved it here; any new static field
     * initializer in the outer class has to go into a holder like this one.
     */
    private static final class ListCensus {
        static final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap REPLAYS =
                new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        static final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap CALLS =
                new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
        /** {@code glCallList} names that resolved to no stored list: the spec ignores them, so geometry is lost. */
        static final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap UNRESOLVED =
                new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
    }
    /** How many replays were refused because the list was nested deeper than {@link #MAX_LIST_NESTING}. */
    private static long listReplayNestingOverflow;
    /**
     * {@code glCallList} calls whose name was not a list at all ({@code -1} or {@code 0}).
     * <p>
     * Kept apart from the unresolved count because the two mean opposite things about the picture: a non-positive name
     * is the caller saying "this part has no list" and GL defines the call as a no-op, while a real name that resolves
     * to nothing is geometry the caller expected to draw and did not. The entity leg {@code r41a-entities} measured 1250
     * of the former and none of the latter, which the single counter reported as {@code unresolved=1250}.
     */
    private static long listCallsWithoutAList;

    /**
     * {@return the display-list measurement: compiled, replayed, unresolved, held, then the busiest lists}.
     *
     * <p>Reported as named totals rather than a bare {@code none}, because the totals are what answer the reported
     * defect: a run that compiled lists and replayed none has every model in the frame drawn by nobody, and that has to
     * be readable from the line instead of inferred from the absence of a token. {@code top} carries each id's replay
     * count and the recorded calls one replay executes, which is what says whether a replay is a model (dozens of calls)
     * or a sky quad (two).
     */
    public static String listCensus() {
        CeltiumGlStub stub = installed;
        StringBuilder text = new StringBuilder();
        text.append("compiled=").append(stub == null ? 0L : GlsmDisplayListLedger.current().compiled())
                .append(" replayed=").append(stub == null ? 0L : GlsmDisplayListLedger.current().replayed())
                .append(" unresolved=").append(stub == null ? 0L : GlsmDisplayListLedger.current().unresolved())
                .append(" noList=").append(listCallsWithoutAList)
                .append(" nestingOverflow=").append(listReplayNestingOverflow)
                .append(" held=").append(stub == null ? 0 : GlsmDisplayListLedger.current().heldLists())
                .append(" heldBytes=").append(stub == null ? 0L : stub.heldListGeometryBytes())
                .append(" distinct=").append(ListCensus.REPLAYS.size());
        if (ListCensus.REPLAYS.isEmpty() && ListCensus.UNRESOLVED.isEmpty()) {
            return text.toString();
        }
        text.append(" top=[");
        // Bounded copy, sorted by replay count: the map holds every list the process ever allocated and this line is
        // printed on every verification pass.
        it.unimi.dsi.fastutil.ints.Int2IntMap.Entry[] ranked = ListCensus.REPLAYS.int2IntEntrySet()
                .toArray(new it.unimi.dsi.fastutil.ints.Int2IntMap.Entry[0]);
        java.util.Arrays.sort(ranked, (a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()));
        for (int i = 0; i < ranked.length && i < 10; i++) {
            int id = ranked[i].getIntKey();
            if (i > 0) {
                text.append(',');
            }
            text.append(id).append(":x").append(ranked[i].getIntValue())
                    .append("/calls=").append(ListCensus.CALLS.get(id));
        }
        text.append(']');
        if (!ListCensus.UNRESOLVED.isEmpty()) {
            text.append(" unresolvedTop=[");
            int unresolvedWritten = 0;
            for (it.unimi.dsi.fastutil.ints.Int2IntMap.Entry entry : ListCensus.UNRESOLVED.int2IntEntrySet()) {
                if (unresolvedWritten >= 10) {
                    break;
                }
                text.append(unresolvedWritten == 0 ? "" : ",").append(entry.getIntKey())
                        .append(":x").append(entry.getIntValue());
                unresolvedWritten++;
            }
            text.append(']');
        }
        return text.toString();
    }

    /** {@return how many replays were refused for nesting past the limit}, for the CPU contract test. */
    static long listReplayNestingOverflows() {
        return listReplayNestingOverflow;
    }

    /**
     * {@return whether the stub holds a recorded command list for this name}.
     * <p>
     * The model path asks this before it reports a list as missing: a list with no captured model quads can still be
     * replayable, because the stub records the commands of a list compiled through {@code GlStateManager.glNewList} and
     * a Tessellator draw inside it becomes a {@code COMPILED_ARRAYS} entry. A mod's skybox is exactly that shape, and
     * reporting it as a missing mesh counted geometry that the GL replay draws.
     */
    public static boolean hasRecordedList(int list) {
        CeltiumGlStub stub = installed;
        return stub != null && list > 0 && GlsmDisplayListLedger.current().hasList(list);
    }

    /**
     * Registers one replay into the census without a GL call, so the CPU check can prove an id above the old array's
     * 512 is counted at all.
     *
     * <p>The failure this guards against is not hypothetical: the previous census was an {@code int[512]} keyed by the
     * GL name, every model list in every measured run has an id above 110000, and the line therefore read
     * {@code celtium/lists[none]} while the run replayed model lists every frame. A summary that says
     * {@code replayed=0} on such a run is indistinguishable from a summary that cannot count, so the check writes a high
     * id and reads the line back - the same positive control the no-op and drawing censuses carry.
     */
    public static void celtium$noteListReplayForCheck(int list, boolean resolved) {
        if (resolved) {
            noteListReplayed(list, 1L);
        } else if (list <= 0) {
            listCallsWithoutAList++;
        } else {
            ListCensus.UNRESOLVED.addTo(list, 1);
        }
    }

    /** The one place a resolved replay is filed, so the totals and the per-list table cannot disagree. */
    private static void noteListReplayed(int list, long recordedCalls) {
        ListCensus.REPLAYS.addTo(list, 1);
        ListCensus.CALLS.addTo(list, recordedCalls);
    }

    /** How deep a replay currently is. glCallList may appear inside a list; the spec puts the limit at 64 or more. */
    private int replayDepth;
    private static final int MAX_LIST_NESTING = 64;
    /** How many compiled lists print their contents, so a session with thousands of compiles cannot flood the log. */
    private static int listCompileLogs;
    private static final int LIST_COMPILE_LOGS = 16;
    /** How many abandoned compiles print, bounded like {@link #listCompileLogs}. */
    private static int listAbandonedLogs;
    /**
     * The client-array pointer state: {@code glVertexPointer(size, type, stride, pointer)}. GL keeps exactly one of
     * these, and it is the only thing that says how the bound array buffer's bytes are to be read - the bytes alone
     * are anonymous. Recorded rather than no-opped for the same reason the buffer storage is mirrored: a draw that
     * arrives without it cannot be decoded, and guessing a layout would submit the wrong bytes rather than none.
     */
    // GAP-ST-8 rewired: the legacy vertex-pointer tuple lives in GlsmClientArrayState
    // (clientPointer(GL_VERTEX_ARRAY)); VERTEX_POINTER below is that live record.
    private static final GlsmClientArrayState.Pointer VERTEX_POINTER =
            GlsmClientArrayState.current().clientPointer(0x8074, true);
    /** The split client arrays, for the stride-48 repack; a normal is recorded but never drawn. */
    private volatile int texCoordPointerSize, texCoordPointerType, texCoordPointerStride;
    private volatile long texCoordPointerOffset;
    private volatile int colorPointerSize, colorPointerType, colorPointerStride;
    private volatile long colorPointerOffset;
    private volatile int normalPointerType, normalPointerStride;
    private volatile long normalPointerOffset;
    /** Per-draw bases of the split arrays, already advanced by {@code first} and validated readable. */
    private long texColorRepackTexBase, texColorRepackColorBase;
    private boolean drawClientMemory, texColorRepackArmed;
    private int texColorRepackSourceStride = 48;
    private int texColorRepacks, texColorRepackDumps;

    /** The installed stub - one per process; GL capabilities are process-global so this is a singleton. */
    static CeltiumGlStub installed;

    /**
     * Gives LWJGL3's own GL bindings addresses from this stub, so a mod that calls them does not abort the JVM.
     * <p>
     * Measured 2026-10-15 (leg {@code gv-e2e-268-no-randompatches}, 268-mod pack minus RandomPatches): with the
     * stub installed and the window created GL-less, the run died at 38.75 s with
     * <pre>
     * FATAL ERROR in native method: No context is current or a function that is not available in the current
     * context was called. The JVM will abort execution.
     *     at org.lwjgl.opengl.ARBShaderObjects.glCreateShaderObjectARB(Native Method)
     *     at thaumcraft.client.lib.ender.ShaderHelper.createShader(ShaderHelper.java:121)
     * </pre>
     * {@code GL.create(provider)} supplies the <em>library</em> the GL classes ask for, but LWJGL3's generated GL
     * classes are {@code public static native} and take their addresses from the thread's {@code GLCapabilities},
     * which is built by {@code GL.createCapabilities()} - and that needs a current context. This stub answers
     * {@code glGetError}, {@code glGetIntegerv} and {@code glGetString} itself, so the capability query succeeds
     * against the stub's own identity and the resulting addresses are this provider's trampolines. That turns a mod
     * calling LWJGL3 GL entry points from "the JVM aborts" into "the stub answers", which is the whole point of the
     * GL-less window.
     * <p>
     * Failure is logged and never fatal: a run without capabilities is exactly the run this project had before, and
     * the census and the validation report stay readable either way.
     */

    /**
     * Builds LWJGL3's {@code GLCapabilities} from this stub and installs it, and must be called <b>before</b> anything
     * else creates one.
     * <p>
     * Measured offline 2026-10-15 with the game's own LWJGL 3.4.1 jars: a capabilities object built with an
     * <b>empty</b> extension set - which is what {@code GL.createCapabilities()} produces, because this stub answers
     * {@code glGetString(GL_EXTENSIONS)} with an empty string on purpose - has
     * {@code GL_ARB_shader_objects=false} and {@code glCreateShaderObjectARB=0x0}, so the call aborts the JVM. Built
     * with the 427 extension names the capabilities class itself declares, the same fields are {@code true} and a real
     * address. Both have 2236 non-zero entries, so "is the table populated" does not separate them; the extension set
     * does.
     * <p>
     * Being first matters because LWJGL's ICD is write-once: the first capabilities set supplies the addresses the
     * generated classes read, and a later different one only falls back to a thread lookup (with a warning that is
     * suppressed unless LWJGL debug is on). That is why installing ours later - from a mixin entry point - changed
     * nothing, and why installing it on a thread while an empty set was already the first made every GL call abort
     * instead of throwing.
     */
    public static synchronized void installFirstCapabilities() {
        if (installed == null || capabilitiesInstalled) {
            return;
        }
        try {
            Class<?> capabilitiesClass = Class.forName("org.lwjgl.opengl.GLCapabilities");
            // Both the extension names (GL_*) and the version pseudo-extensions (OpenGL11 ... OpenGL46): the
            // generated checks ask for a version with ext.contains("OpenGL30") as well as for extension names, so a
            // set built from GL_* alone leaves core functions gated on a version at zero. Measured offline before any
            // leg: glGetString came out 0x0 with only the GL_* names and non-zero with both.
            java.util.Set<String> extensions = new java.util.HashSet<>();
            // The version pseudo-extensions are cut off at the version this stub actually advertises (3.0). Adding
            // every OpenGL* field made capabilities.OpenGL44 true while glGetString(GL_VERSION) said 3.0 - the same
            // inconsistency the version integers had, and the same 4.4 gate (MappedStagingBuffer) that stalled the
            // client on the persistent-mapping path. The GL_* extension flags stay as they were.
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
                    org.lwjgl.system.FunctionProvider.class, java.util.Set.class, boolean.class,
                    java.util.function.IntFunction.class);
            constructor.setAccessible(true);
            Object capabilities = constructor.newInstance(installed, extensions, Boolean.FALSE,
                    (java.util.function.IntFunction<org.lwjgl.PointerBuffer>) org.lwjgl.BufferUtils::createPointerBuffer);
            org.lwjgl.opengl.GL.setCapabilities((org.lwjgl.opengl.GLCapabilities) capabilities);
            capabilitiesInstalled = true;
            GlsmHintLedger.current().noteCapabilitiesInstalled(extensions.size());
            celtium$printAtExit("CELTIUM_GLSTUB LWJGL3 capabilities installed first from the stub provider: extensions="
                    + extensions.size() + " thread=" + Thread.currentThread().getName());
        } catch (Throwable error) {
            celtium$printAtExit("CELTIUM_GLSTUB first capabilities install failed: " + error);
        }
    }

    /** True once {@link #installFirstCapabilities()} has set this stub's capabilities. */
    private static volatile boolean capabilitiesInstalled;

    /**
     * Installs this provider as the process's GL function source; call before {@code GL.createCapabilities()}.
     * LWJGL only lets {@code GL.create} supply the function source when the library's default load was deferred,
     * so this also asserts {@link Configuration#OPENGL_EXPLICIT_INIT} - the window-creation path must set it before
     * anything touches the GL class.
     */
    public static synchronized CeltiumGlStub install() {
        if (installed == null) {
            installed = new CeltiumGlStub();
            GlsmHintLedger.current().noteStubInstalled();
            GlsmHintLedger.current().noteFunctionProviderInstalled();
            org.lwjgl.system.Configuration.OPENGL_EXPLICIT_INIT.set(true);
            org.lwjgl.opengl.GL.create(installed);
            // No GLCapabilities is built here on purpose. Measured 2026-10-15: installing one converts LWJGL3's
            // catchable "no capabilities" exception into an uncatchable JVM abort whenever a function's address is
            // still zero, which broke the cohort (Flare's GpuInfo.queryGpuModel calls glGetString during mod
            // construction) while leaving the pack's own abort in place. The LWJGL3 surface is handled by
            // CeltiumGllessNativeTransformer instead, which replaces the native methods themselves.
            // The census has to be readable after the run: the harness reads logs, and the renderer's own report
            // only carries the strings it knows to ask for. One line at exit, bounded. The glDrawArrays line sits
            // beside it because the two answer the same question from opposite sides: the census says which drawing
            // entry points reached no image, and the array line says what happened to the one that used to be on it.
            //
            // Written to the process's own stdout rather than through System.out, and that is a measurement, not a
            // preference: Forge routes System.out into Log4j, Log4j's own shutdown hook stops the appenders, and the
            // shutdown hooks run concurrently - so this line came out or did not depending on which hook won. Measured
            // over three runs of the same fixture: one run has CELTIUM_NOOP_CENSUS in its log, two have none (the
            // font capture's hook line appears in one of those, so hook output is not lost wholesale - it is a race).
            // A census that exists only in the runs that win a race is not an instrument; FileDescriptor.out is the
            // file the process was started with and stays writable through the teardown. The content is unchanged.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                celtium$printAtExit("CELTIUM_NOOP_CENSUS " + noopCensusSummary());
                celtium$printAtExit("CELTIUM_UNKNOWN_SYMBOLS " + unknownSymbolSummary());
                // BH-07/BH-10: the blend and colour-write record's own census, printed only when one of the entry
                // points was reached. Before the fix every one of these counts was zero because all five functions
                // were in the no-op table; a run that never blends still stays silent rather than claiming a
                // measurement it did not take.
                if (installed != null && (installed.blendFuncCalls.get() > 0L || installed.blendSeparateCalls.get() > 0L
                        || installed.blendEquationCalls.get() > 0L || installed.colorMaskCalls.get() > 0L)) {
                    celtium$printAtExit("CELTIUM_BLEND_CENSUS " + blendCensusSummary());
                }
                if (GlDrawCensus.DRAWARRAY_CALLS.get() > 0L) {
                    celtium$printAtExit("CELTIUM_DRAWARRAYS " + drawArraySummary());
                }
                if (GlDrawCensus.DrawElementCensus.CALLS.get() > 0L) {
                    celtium$printAtExit("CELTIUM_DRAWELEMENTS " + drawElementSummary());
                }
                if (installed != null && (installed.textureParameters.calls() > 0 || installed.textureParameters.queries() > 0)) {
                    // The texture-parameter record's own census: what the game set, and what the queries were answered
                    // from. Printed only when something was recorded or asked, so a run that never touches it stays
                    // silent rather than claiming a measurement it did not take.
                    celtium$printAtExit("CELTIUM_TEX_PARAM_CENSUS " + texParamSummary());
                }
                if (FN_CENSUS) {
                    celtium$printAtExit("CELTIUM_FN_CENSUS " + fnCensusSummary());
                }
                // Display lists: printed only when one was compiled or replayed, so a run that never touches them
                // stays silent rather than claiming a measurement it did not take. `replayed` is the number that
                // matters for the reported defects - it is the count of glCallList calls that found a list and drew
                // it, and `unresolved` is the count that found nothing, which the spec says is ignored. The format is
                // listCensus()'s, so this hook and the verification line cannot report two different shapes of the
                // same measurement (the hook's output is not in the log, which round 20 measured; the line is).
                if (installed != null && (GlsmDisplayListLedger.current().compiled() > 0 || GlsmDisplayListLedger.current().replayed() > 0
                        || GlsmDisplayListLedger.current().unresolved() > 0)) {
                    celtium$printAtExit("CELTIUM_DISPLAY_LISTS " + listCensus());
                }
            }, "celtium-noop-census"));
        }
        return installed;
    }

    /**
     * BH-03's one-flag control: the alpha comparison function the immediate path applies, overriding whatever the game
     * set. {@code -Dceltium.glstub.alphaFuncOverride=<GL enum>} (GL_LEQUAL 0x0203, GL_ALWAYS 0x0207, ...), with 0
     * meaning "use the game's own function". It exists because the game sets its own functions and a fixture knob for
     * every piece of GL state is what this avoids: the same jar, scene and crop can then be compared under two
     * functions, which is how the shader's comparison switch is verified rather than assumed.
     */
    public static int alphaFuncOverride() {
        return ALPHA_FUNC_OVERRIDE;
    }

    /** Read once: the override is a launch flag, and the state capture asks for it per offer. */
    private static final int ALPHA_FUNC_OVERRIDE = Integer.getInteger("celtium.glstub.alphaFuncOverride", 0).intValue();

    /**
     * Reserves {@code range} names from the one window every generated GL name comes from.
     *
     * <p>There were two windows: this class's {@code nextName} for calls that arrived through the stub, and a second
     * counter in the Forge-side hint for calls the transformer rewrote. Both started at 1, so a buffer name handed out
     * by one could be the same integer as a display-list name handed out by the other - and the mirrors are keyed by
     * name, so that is a collision rather than a cosmetic duplicate.
     *
     * @return the first of {@code range} contiguous names, or {@code -1} when no stub is installed
     */
    public static int celtium$reserveNames(int range) {
        if (range <= 0) {
            return 0;
        }
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return -1;
        }
        int scalar = RenderObjectIds.reserve(range);
        for (int i = 0; i < range; i++) {
            GlsmNameLedger.current().addName(scalar + i);
        }
        return scalar;
    }

    /**
     * Whether this stub answers GL calls in this process. When it does there is no GL device identity to read -
     * the renderer's strings are ours, not a driver's.
     */
    public static boolean isInstalled() {
        return installed != null;
    }

    /**
     * {@return the display list {@code glCallList} is currently executing, or 0 when none is}.
     *
     * <p>{@code GL_LIST_INDEX} answers a different question and the difference is the whole reason this accessor
     * exists: the query names the list being <em>compiled</em>, and the spec keeps it at 0 while a list is being
     * executed. Minecraft's model rendering compiles once and then draws with {@code glCallList(displayList)}, so
     * during the draw the query answers 0 while a list is in fact executing - and the model capture, which asked
     * only the query, refused every quad of every model with {@code reason=unknown-list list=0} and drew nothing.
     * Answering {@code GL_LIST_INDEX} from the recording state fixed the compile side; this is the replay side.
     */
    public static int executingList() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : GlsmDisplayListLedger.current().executingListId();
    }

    /**
     * {@return the display list {@code glNewList} is currently compiling, or 0 when none is}.
     *
     * <p>This is the same value {@code GL_LIST_INDEX} is supposed to answer, and the stub does answer that pname.
     * The model capture nevertheless read 0 from the query: the leg's function census shows {@code glGetIntegerv}
     * arriving four times with the capability pnames {@code 0x821B}..{@code 0x821E} and never with
     * {@code 0x0B33}, so the capture's call does not reach this stub at all and LWJGL answers it - with 0, because
     * there is no GL context under GL-less. Asking here is the same fix the replay side already needed, and it
     * does not depend on which path LWJGL happens to take.
     */
    public static int compilingList() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : GlsmDisplayListLedger.current().compiling();
    }

    /**
     * Records the display list {@code glNewList} opened, from the layer that sees the call.
     *
     * <p>Needed because the two layers are not the same one under GL-less: the game's display-list calls reach
     * {@code CeltiumGllessNativeTransformer}, which rewrites them to {@code CeltiumGllessHint} and returns, so
     * {@link #compilingList} - the field this stub sets from its own dispatch - is only written when a call travels
     * the trampoline, which these do not. Measured on leg {@code gv-cohort-dl9}: the hint counted 37
     * {@code glNewList} arrivals while every one of the 222 model quads was filed with {@code list=-1}, because the
     * capture reads this field. Pushed rather than pulled so the reader stays one field read.
     */
    public static void celtium$noteListOpened(int list, int mode) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        GlsmDisplayListLedger.current().compiling(list);
        GlsmDisplayListLedger.current().compilingMode(mode);
        // Where the current colour and texture coordinate came from decides whether a compiled run keeps its baked
        // bytes or takes the values current at glCallList; see CompiledLegacyRun. A name of zero is not a list.
        if (list > 0) {
            stub.openCompileState(mode);
        }
        // The model capture takes a generation per compile. The vanilla path notifies it from its own mixin before
        // this call (a harmless second reset, since no quad arrives between them); a mod's raw glNewList reaches only
        // this point, and without the notification its recompiled list kept the mesh built from the old contents.
        if (list > 0) {
            org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listCompileStarted(list);
        }
    }

    /** Direct Mod GL entry points share the compatibility compiler, including transformed LWJGL calls. */
    public static void compatibilityNewList(int list, int mode) {
        CeltiumGlStub stub = installed;
        if (stub == null) return;
        long[] c = stub.dispatchArgsScratch(); c[0] = list; c[1] = mode;
        stub.dispatchArgs(IDS.get("glNewList"), c);
    }

    public static void compatibilityEndList() {
        CeltiumGlStub stub = installed;
        if (stub != null) stub.dispatchArgs(IDS.get("glEndList"), stub.dispatchArgsScratch());
    }

    public static void compatibilityCallList(int list) {
        CeltiumGlStub stub = installed;
        if (stub == null) return;
        if (stub.openList == null) stub.replayDisplayList(list);
        else {
            long[] c = stub.dispatchArgsScratch(); c[0] = list;
            stub.dispatchArgs(IDS.get("glCallList"), c);
        }
    }

    /** Closes the list {@link #celtium$noteListOpened} opened, as {@code glEndList} does. */
    public static void celtium$noteListClosed() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        GlsmDisplayListLedger.current().compiling(0);
        GlsmDisplayListLedger.current().compilingMode(0);
        stub.closeCompileState();
    }

    /**
     * Snapshots the colour state a {@code GL_COMPILE} list must leave untouched, from whichever layer saw
     * {@code glNewList} first.
     *
     * <p>Both layers report the call - the stub's own dispatch and the transformer hint the game's calls reach - so a
     * second arrival for the same compile is dropped rather than re-snapshotting: by the second arrival the list may
     * already have recorded a colour, and snapshotting that would make the rollback restore the list's own value.
     *
     * @param mode {@code GL_COMPILE} (0x1300) or {@code GL_COMPILE_AND_EXECUTE} (0x1301)
     */
    private void openCompileState(int mode) {
        if (this.legacyCapture == null) {
            return;
        }
        // The capture owns both snapshots - its own mirror and GL_CURRENT_COLOR, which every colour route writes and
        // the backend's group tint reads - so the mode that decides the rollback and the values it rolls back cannot
        // disagree, and a second arrival (the other layer's report of the same glNewList) is dropped inside.
        this.legacyCapture.openCompile(mode, GlsmImmediateState.current().red(), GlsmImmediateState.current().green(), GlsmImmediateState.current().blue(), GlsmImmediateState.current().alpha());
    }

    private void closeCompileState() {
        if (this.legacyCapture == null || !this.legacyCapture.closeCompile()) {
            return;
        }
        GlsmImmediateState.current().color(this.legacyCapture.callerRed(), this.legacyCapture.callerGreen(),
                this.legacyCapture.callerBlue(), this.legacyCapture.callerAlpha());
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend != null) {
            // One state call per compile, and models compile once: the colour the list's compile must not tint the
            // next group with.
            backend.setColor(GlsmImmediateState.current().red(), GlsmImmediateState.current().green(), GlsmImmediateState.current().blue(), GlsmImmediateState.current().alpha());
        }
    }

    /**
     * The texture-env int write, reached from the transformer layer: lwjglx delegates the public GL11 form to the
     * real LWJGL3 {@code org.lwjgl.opengl.GL11}, whose {@code ngl*} natives the GL-less native transformer replaces
     * - without an entry point here the writes die in the replaced body and hurt-tint's COMBINE env never lands
     * (measured on legs plain-hurt3/4: zero CELTIUM_ENV_WRITE despite the hurt zombie rendering).
     */
    public static void celtium$glTexEnvi(int target, int pname, int param) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.celtium$setTexEnvInt(target, pname, param);
    }

    /** The float form of the same write, for {@code nglTexEnvf}; enum-valued pnames round, as GL does. */
    public static void celtium$glTexEnvf(int target, int pname, float param) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.celtium$setTexEnvInt(target, pname, Math.round(param));
    }

    /** The pointer forms, {@code nglTexEnvfv}/{@code nglTexEnviv}: a float colour or a first-element enum. */
    public static void celtium$glTexEnvPtr(int target, int pname, long values) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.celtium$setTexEnvFloats(target, pname, values);
    }

    /** The int read-back forms, {@code nglGetTexEnvi}/{@code nglGetTexEnviv}: one int through the out pointer. */
    public static void celtium$glGetTexEnvInt(int target, int pname, long out) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.celtium$getTexEnvInt(target, pname, out);
    }

    /** The float read-back form, {@code nglGetTexEnvfv}: four floats through the out pointer. */
    public static void celtium$glGetTexEnvFloats(int target, int pname, long out) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.celtium$getTexEnvFloats(target, pname, out);
    }

    /** {@code nglGetTexEnvi}'s int-returning form: the recorded int on the active unit, 0 when no stub is installed. */
    public static int celtium$glGetTexEnvI(int target, int pname) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return 0;
        }
        return org.soutalab.celtium.vulkan.NativeTextureEnvironment.current().activeParameter(target, pname);
    }

    /** Compatibility entry for callers publishing a 2D bind; normal Minecraft binding writes the native owner. */
    public static void celtium$noteBoundTexture(int texture) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.textures.bindTexture2d(texture);
        stub.lastBoundTexture = texture;
        stub.noteBindSite(texture);
    }

    /**
     * Records a {@code glBindTexture} the transformer's route carries, the same record the dispatch case makes.
     *
     * <p>Before the route existed the rewritten native was a no-op: a mod's raw {@code GL11.glBindTexture} - and this
     * project's own {@code whiteTextureId()} - bound nothing the stub could see, so the upload that followed was
     * filed under whatever {@link #lastBoundTexture} still named. Measured consequence on the E2E pack: the white
     * 1x1 pixel landed on the atlas's capture record, the synthetic texture got name 506 with no extent and no
     * pixels, and every texturing-off batch was refused "no mirror for this GL id" forever.
     */
    public static void celtium$glBindTexture(int target, int texture) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        if (target == GL_TEXTURE_2D) stub.textures.bindTexture2d(texture);
        else stub.textureBindings.put(stub.texBindKey(target), texture);
        stub.lastBoundTexture = texture;
        stub.noteBindSite(texture);
    }

    /** Records a {@code glActiveTexture} the transformer's route carries, the same record the dispatch case makes. */
    public static void celtium$glActiveTexture(int unit) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.textures.activeUnit(unit - 0x84C0);
    }

    /**
     * Serves {@code glDeleteTextures} the transformer's route carries, the same retire-the-records work the dispatch
     * case does; without the route a deleted texture kept its stale capture, which a reallocated name then read as
     * its own.
     */
    public static void celtium$glDeleteTextures(int count, long names) {
        CeltiumGlStub stub = installed;
        if (stub == null || names == 0L) {
            return;
        }
        for (int i = 0; i < count; i++) {
            int name = memGetInt(names + (long) i * 4L);
            GlsmNameLedger.current().removeName(name);
            stub.deleteTexture(name);
        }
    }

    /** Records the transformer's glDeleteFramebuffers(count, pointer) route. */
    public static void celtium$glDeleteFramebuffers(int count, long names) {
        CeltiumGlStub stub = installed;
        if (stub == null || names == 0L) return;
        for (int i = 0; i < count; i++) {
            int name = memGetInt(names + (long) i * 4L);
            GlsmNameLedger.current().removeName(name);
            stub.forgetFramebuffer(name);
        }
    }

    /**
     * Remembers the first caller that bound each texture name. A stack walk is paid once per name (the map lookup
     * keeps repeat binds cheap), so the common hot binds cost a contains-key and nothing else.
     */
    private void noteBindSite(int texture) {
        // The table-full test first: once it has reached its cap this method is asked on every texture bind and can
        // only ever answer "already full", so the map probe must not be paid for. The order is also what keeps the
        // cost bounded - a bind is one of the most frequent GL calls in the frame, and this is a diagnostic, not
        // state the draw reads.
        if (!BIND_SITE_DIAGNOSTICS || texture == 0 || this.firstBindSites.size() >= MAX_BIND_SITES
                || this.firstBindSites.get(texture) != null) {
            return;
        }
        this.firstBindSites.put(texture, celtium$callSite() + " unit=" + this.textures.activeUnit());
    }

    /**
     * {@return the caller that first bound this texture name, or {@code "none"} when no bind was ever seen}. The
     * answer for the "no mirror" refusal's texture is the producer nobody could otherwise name.
     */
    public static String firstBindSite(int textureName) {
        CeltiumGlStub stub = installed;
        String site = stub == null ? null : stub.firstBindSites.get(textureName);
        return site == null ? "none" : site;
    }

    /**
     * Records a texture parameter against the texture it applies to, from the transformer's route.
     *
     * <p>{@code glTexParameteri} is one of the calls the transformer rewrites, so it has to be handed back here or
     * nothing records it (the terrain path's own comment on the display-list family). Same target/pname/value trip the
     * stub's own dispatch would record, through the shared producer.
     */
    public static void celtium$texParameteri(int target, int pname, int value) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.celtium$setTextureParameterBound(target, pname, value, "transformer");
    }

    /** The direct-state-access spellings, where the first argument is the texture name. */
    public static void celtium$textureParameteri(int texture, int pname, int value) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.celtium$setTextureParameter(texture, pname, value, "transformer");
        }
    }

    public static void celtium$textureParameterf(int texture, int pname, float value) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.celtium$setTextureParameter(texture, pname, Math.round(value), "transformer");
        }
    }

    public static void celtium$textureParameterPointer(int texture, int pname, long values, boolean floats) {
        CeltiumGlStub stub = installed;
        if (stub == null || values == 0L) {
            return;
        }
        int value = floats ? Math.round(org.lwjgl.system.MemoryUtil.memGetFloat(values))
                : org.lwjgl.system.MemoryUtil.memGetInt(values);
        stub.celtium$setTextureParameter(texture, pname, value, "transformer");
    }

    /** {@code glTexParameterf} from the transformer's route. */
    public static void celtium$texParameterf(int target, int pname, float value) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.celtium$setTextureParameterBound(target, pname, Math.round(value), "transformer");
        }
    }

    /** The pointer forms of the texture-parameter setter; {@code floats} selects how the first value is read. */
    public static void celtium$texParameterPointer(int target, int pname, long values, boolean floats) {
        CeltiumGlStub stub = installed;
        if (stub == null || values == 0L) {
            return;
        }
        int value = floats ? Math.round(org.lwjgl.system.MemoryUtil.memGetFloat(values))
                : org.lwjgl.system.MemoryUtil.memGetInt(values);
        stub.celtium$setTextureParameterBound(target, pname, value, "transformer");
    }

    /**
     * Serves a {@code glCopyTexSubImage2D}, from the transformer's route.
     *
     * <p>Same producer the stub's own dispatch calls, so the two spellings cannot diverge; this is the call the main
     * menu's panorama needs (see {@link #celtium$noteTextureCopy}).
     */
    public static void celtium$copyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y,
            int width, int height) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.celtium$noteTextureCopy("glCopyTexSubImage2D", target, level, xoffset, yoffset, x, y, width, height);
    }

    /**
     * Serves a {@code glCopyImageSubData}, from the transformer's route.
     *
     * <p>Same producer the stub's own dispatch calls, so the two spellings cannot diverge; this is the call
     * FoamFix's fast animation path makes every tick (see {@link #copyImageSubData}).
     */
    public static void celtium$copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY,
            int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
            int width, int height, int depth) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.copyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, dstName, dstTarget, dstLevel,
                    dstX, dstY, dstZ, width, height, depth);
        }
    }

    /**
     * Serves a {@code glCopyTexImage2D}, from the transformer's route.
     * <p>
     * A depth-format destination is not served: the copy service reads the read framebuffer's colour image, so serving
     * it wrote colour into a depth texture, and every call paid a flush, a queue wait and a full-screen readback. The
     * measured caller is The Betweenlands ({@code DepthBuffer.blitDepthBuffer}, every frame, 6402 = GL_DEPTH_COMPONENT)
     * whose only reader is its own GLSL post-processing, which GL-less does not run. The extent is still recorded, so
     * size queries answer as GL would; {@code depthCopiesSkipped} counts the calls.
     */
    public static void celtium$copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width,
            int height) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        if (isDepthInternalFormat(internalFormat)) {
            stub.recordExtent(target, level, internalFormat, width, height);
            if (stub.depthCopiesSkipped++ == 0) {
                System.out.println("[Celtium] CELTIUM_DEPTH_COPY_SKIPPED internalFormat=0x"
                        + Integer.toHexString(internalFormat) + " " + width + "x" + height
                        + " (a depth copy is not read back; the size is recorded)");
            }
            return;
        }
        stub.celtium$noteTextureCopy("glCopyTexImage2D", target, level, 0, 0, x, y, width, height);
    }

    private long depthCopiesSkipped;

    // GAP-ST-10: the null-allocated classification also lives in GlsmNameLedger.

    /** GL_DEPTH_COMPONENT, DEPTH_COMPONENT16/24/32, DEPTH_COMPONENT32F, DEPTH24_STENCIL8, DEPTH32F_STENCIL8, DEPTH_STENCIL. */
    private static boolean isDepthInternalFormat(int internalFormat) {
        return switch (internalFormat) {
            case 0x1902, 0x81A5, 0x81A6, 0x81A7, 0x8CAC, 0x88F0, 0x8CAD, 0x84F9 -> true;
            default -> false;
        };
    }

    /**
     * The client-array/VBO draw family, from the transformer's route.
     *
     * <p>These are the natives behind {@code VertexBuffer}: {@code bindBuffer -> glEnableClientState/glVertexPointer
     * -> glDrawArrays}. The stub already decodes exactly this call shape in {@link #drawArrays(int, int, int)} - the
     * sky dome, the stars, the horizon plane and Forge's own {@code CloudRenderer} all draw through it - but under
     * GL-less the transformer rewrote the natives to no-ops before the stub could see them, which is why those four
     * were missing from every picture while no counter named them (leg gv-cohort-cloud2's CELTIUM_TRANSFORM_CENSUS:
     * {@code glDrawArrays=6220 nglVertexPointer=5332 glEnableClientState=7117 glBindBuffer=12474}).
     */
    public static void celtium$drawArrays(int mode, int first, int count) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            long[] c = stub.dispatchArgsScratch(); c[0] = mode; c[1] = first; c[2] = count;
            stub.dispatchArgs(IDS.get("glDrawArrays"), c);
        }
    }

    /** Records the array-buffer binding, as the stub's own {@code glBindBuffer} case does. */
    public static void celtium$bindBuffer(int target, int name) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            if (target == GL_ARRAY_BUFFER) {
                NativeGeometryBuffer.bindId(name);
            }
            return;
        }
        stub.bindBuffer(target, name);
    }

    private void bindBuffer(int target, int name) {
        if (target == GL_ARRAY_BUFFER) {
            NativeGeometryBuffer.bindId(name);
        } else if (target == GL_PIXEL_PACK_BUFFER) {
            this.pixelPackBuffer = name;
            return;
        } else if (target == GL_PIXEL_UNPACK_BUFFER) {
            this.pixelUnpackBuffer = name;
            return;
        }
        GlsmBufferState.current().bind(target, name);
    }

    /** Refusals caused by a bound pixel buffer, counted once so the diagnostic is bounded. */
    int pixelBufferRefusals;
    /** Bounded count of the CELTIUM_PBO_UPLOAD lines: texture uploads read from a bound pixel unpack buffer. */
    int pboUploadLogs;

    /** {@code GL_PIXEL_PACK_BUFFER}: with one bound, a readback's {@code pixels} argument is a buffer offset, not a pointer. */
    static final int GL_PIXEL_PACK_BUFFER = 0x88EB;

    /** {@code GL_PIXEL_UNPACK_BUFFER}: with one bound, a texture upload's {@code pixels} argument is a buffer offset. */
    static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;

    /** The bound pixel pack buffer, 0 when none is bound. */
    int pixelPackBuffer;

    /** The bound pixel unpack buffer, 0 when none is bound. */
    int pixelUnpackBuffer;

    /**
     * {@return why a readback or upload must be refused when a pixel buffer is bound}, or null when it may proceed.
     * <p>
     * With {@code GL_PIXEL_PACK_BUFFER} or {@code GL_PIXEL_UNPACK_BUFFER} bound, GL's contract says the {@code pixels}
     * argument is an offset into that buffer, not a client address (audit #360). Treating the offset as a pointer - which
     * the readback and upload paths did - reads or writes the JVM's low memory at address 0 or 16. Neither path can honour
     * a buffer offset, so both refuse explicitly instead of dereferencing it; the offset reaches the diagnostic verbatim.
     */
    static String pixelBufferRefusal(int boundPixelBuffer, long offset, String operation) {
        if (boundPixelBuffer == 0) {
            return null;
        }
        if (boundPixelBuffer < 0) {
            return null;
        }
        return operation + " with a pixel buffer bound (buffer=" + boundPixelBuffer + " offset=" + offset
                + "): the argument is a buffer offset, not a client pointer, and this path cannot copy into a PBO";
    }

    /**
     * Records one capability enable/disable, from the transformer's route.
     *
     * <p>The client-state spellings ({@code glEnableClientState}/{@code glDisableClientState}) and the plain
     * {@code glEnable}/{@code glDisable} both land here, so the record the draw path reads is the game's own state
     * whichever spelling the caller used. Leaving {@code glEnable}/{@code glDisable} out is what drew the clouds
     * untextured: measured on leg gv-cohort-cloud9, {@code texture2dEnabled=false} at the cloud VBO's draw while its
     * UVs decoded correctly.
     */
    public static void celtium$clientState(int capability, boolean enabled) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.setCapability(capability, enabled);
        }
    }

    /** {@code glCopyBufferSubData} from the transformer's route, resolving both targets' bindings. */
    public static void celtium$copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset,
            long size) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.copyBuffer(stub.boundBuffer(readTarget), stub.boundBuffer(writeTarget), readOffset, writeOffset, size);
        }
    }

    /** The DSA spelling, where both arguments are buffer names. */
    public static void celtium$copyNamedBufferSubData(int readBuffer, int writeBuffer, long readOffset,
            long writeOffset, long size) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.copyBuffer(readBuffer, writeBuffer, readOffset, writeOffset, size);
        }
    }

    /** {@code glGetBufferSubData} from the transformer's route, against the bound buffer. */
    public static void celtium$getBufferSubData(int target, long offset, long size, long out) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.readBuffer(stub.boundBuffer(target), offset, size, out);
        }
    }

    /** The DSA spelling, where the first argument is the buffer name. */
    public static void celtium$getNamedBufferSubData(int name, long offset, long size, long out) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.readBuffer(name, offset, size, out);
        }
    }

    /** Reads a byte range out of a buffer mirror into the caller's address. */
    void readBuffer(int name, long offset, long size, long out) {
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
            capacity = nativeBuffer != null ? view.capacity() : this.logicalSizeOf(name, view.capacity());
        } catch (RuntimeException error) {
            return;
        }
        if (mapRangeOffset(offset, size, capacity) < 0L) {
            return;
        }
        MemoryUtil.memCopy(MemoryUtil.memAddress(view) + offset, out, size);
    }

    /** Copies a byte range between two buffer mirrors; the native and heap forms use their own writes. */
    void copyBuffer(int readName, int writeName, long readOffset, long writeOffset, long size) {
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
            readCapacity = nativeRead != null ? source.capacity() : this.logicalSizeOf(readName, source.capacity());
        } catch (RuntimeException error) {
            return;
        }
        if (mapRangeOffset(readOffset, size, readCapacity) < 0L) {
            return;
        }
        ByteBuffer slice = source.duplicate();
        slice.position((int) readOffset);
        slice.limit((int) (readOffset + size));
        slice = slice.slice();
        try {
            NativeGeometryBuffer nativeWrite = NativeGeometryBuffer.find(writeName);
            if (nativeWrite != null) {
                if (mapRangeOffset(writeOffset, size, nativeWrite.byteSize()) >= 0L) {
                    nativeWrite.write(writeOffset, slice);
                }
                return;
            }
            ByteBuffer destination = GlsmBufferState.current().storage(writeName);
            if (destination != null
                    && mapRangeOffset(writeOffset, size, this.logicalSizeOf(writeName, destination.capacity())) >= 0L) {
                ByteBuffer out = destination.duplicate();
                out.position((int) writeOffset);
                out.put(slice);
            }
        } catch (RuntimeException ignored) {
            // A closed or mapped destination keeps its previous content, as the GL copy would have refused.
        }
    }

    /**
     * {@code glDelete*} array names from the transformer's route. The dispatcher's delete path already knows each
     * family (texture payloads, buffer storage, framebuffer attachment records); it only had no caller, because a
     * transformed native never reaches the table. The name is normalised to the core spelling the dispatcher matches.
     */
    public static void celtium$deleteObjects(String name, int count, long names) {
        CeltiumGlStub stub = installed;
        if (stub == null || count <= 0 || names == 0L) {
            return;
        }
        // The count is caller data and the loop reads the name array with it; a bogus one walks past the block. The
        // cap matches glCallLists': far above any legitimate delete batch, small enough to bound a corrupt one.
        int bounded = Math.min(count, 1 << 16);
        stub.delete(new Fn(coreDeleteName(name), R_VOID, new byte[] {A_INT, A_PTR}, SEM_DELETE),
                new long[] {bounded, names});
    }

    /** {@code glDeleteProgram/glDeleteShader} from the transformer's route. */
    public static void celtium$deleteObject(String name, int object) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.delete(new Fn(coreDeleteName(name), R_VOID, new byte[] {A_INT}, SEM_DELETE),
                new long[] {object});
    }

    private static String coreDeleteName(String name) {
        String core = name.startsWith("ngl") ? name.substring(1) : name;
        if (core.endsWith("EXT") || core.endsWith("ARB")) {
            core = core.substring(0, core.length() - 3);
        }
        return core;
    }

    /** {@code glNamedFramebufferTexture} from the transformer's route: the attachment record for a named framebuffer. */
    public static void celtium$namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        CeltiumGlStub stub = installed;
        if (stub == null || framebuffer == 0 || level != 0) {
            return;
        }
        if (isDepthAttachment(attachment)) {
            GlsmFramebufferAttachments.current().attachDepth(framebuffer, texture);
            // Registration point, as in the bound-form attach: the record is complete enough to register from.
            Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                vulkan.celtium$rawAttachmentChanged(framebuffer);
            }
            return;
        }
        int slot = colorAttachmentSlot(attachment);
        if (slot < 0) {
            return;
        }
        GlsmFramebufferAttachments.current().attachColor(framebuffer, slot, texture);
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            vulkan.celtium$rawAttachmentChanged(framebuffer);
        }
    }

    /** The int-returning texture-parameter query, bound or named. */
    public static int celtium$getTextureParameterInt(int targetOrName, int pname, boolean named) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return 0;
        }
        if (named) {
            return textureParameterValue(targetOrName, pname);
        }
        int textureName = 0;
        if (!isProxyTarget(targetOrName)) {
            int bound = targetOrName == GL_TEXTURE_2D ? stub.textures.texture2d()
                    : stub.textureBindings.get(stub.texBindKey(targetOrName));
            textureName = bound < 0 ? 0 : bound;
        }
        return textureParameterValue(textureName, pname);
    }

    /** {@code glGetTextureParameteriv/fv/Iiv/Iuiv}: the DSA spelling of the per-texture parameter record. */
    public static void celtium$getTextureParameterNamed(int texture, int pname, long out, boolean floats) {
        if (out == 0L) {
            return;
        }
        int value = textureParameterValue(texture, pname);
        if (floats) {
            org.lwjgl.system.MemoryUtil.memPutFloat(out, value);
        } else {
            org.lwjgl.system.MemoryUtil.memPutInt(out, value);
        }
    }

    /** {@code glGetTextureLevelParameteriv/i64v}: the DSA spelling of the recorded level extent. */
    public static void celtium$getTexLevelParameterNamed(int texture, int level, int pname, long out, boolean wide) {
        CeltiumGlStub stub = installed;
        if (stub == null || out == 0L) {
            return;
        }
        long value = 0L;
        int[] extent = texture > 0 ? stub.texLevels.get(texLevelKey(texture, level)) : null;
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
            org.lwjgl.system.MemoryUtil.memPutLong(out, value);
        } else {
            org.lwjgl.system.MemoryUtil.memPutInt(out, (int) value);
        }
    }

    /** {@code glTextureSubImage2D} from the transformer's route: a named patch into the mirror. */
    public static void celtium$texSubImageNamed(int texture, int level, int x, int y, int width, int height, int format,
            int type, long pixels) {
        CeltiumGlStub stub = installed;
        if (stub != null && texture > 0) {
            stub.texSubImageForName(texture, level, x, y, width, height, format, type, pixels);
        }
    }

    /** {@code glTextureStorage2D/3D} from the transformer's route: the named form of the extent record. */
    public static void celtium$texStorageNamed(int texture, int levels, int internalFormat, int width, int height) {
        CeltiumGlStub stub = installed;
        if (stub != null && texture > 0 && levels > 0 && width > 0 && height > 0) {
            stub.recordExtentForName(texture, 0, internalFormat, width, height);
        }
    }

    /** {@code glTexStorage2D/3D} from the transformer's route: immutable storage still defines the level-0 extent. */
    public static void celtium$texStorage(int target, int levels, int internalFormat, int width, int height) {
        CeltiumGlStub stub = installed;
        if (stub != null && levels > 0 && width > 0 && height > 0) {
            stub.recordExtent(target, 0, internalFormat, width, height);
        }
    }

    /** {@code glBufferData/glBufferStorage} from the transformer's route, against the bound buffer. */
    public static void celtium$bufferDataTarget(int target, long size, long data) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.bufferDataFor(stub.boundBuffer(target), size, data);
        }
    }

    /** The DSA form, where the first argument is the buffer name. */
    public static void celtium$bufferDataNamed(int name, long size, long data) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.bufferDataFor(name, size, data);
        }
    }

    /** {@code glBufferSubData} from the transformer's route, against the bound buffer. */
    public static void celtium$bufferSubDataTarget(int target, long offset, long size, long data) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.bufferSubDataFor(stub.boundBuffer(target), offset, size, data);
        }
    }

    public static void celtium$bufferSubDataNamed(int name, long offset, long size, long data) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.bufferSubDataFor(name, offset, size, data);
        }
    }

    /** {@code glBindVertexArray}: the VAO name the query answers. */
    public static void celtium$bindVertexArray(int array) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        // Attribute state belongs to the VAO: the per-attribute records live in the VAO's own array, written in
        // place while the VAO is bound, and binding swaps which array is live - so a mod that sets its attributes
        // once and rebinds its VAO draws from its own state and not from whatever the last draw left behind.
        // Name 0 is the default VAO and keeps its own records too: skipping it lost the pointer a caller had set
        // on VAO 0 when it bound another VAO and came back.
        stub.boundVertexArray = array;
        stub.celtium$noteAttribZero();
    }

    /** {@code glPointSize} from the transformer's route: recorded for the query, since points are not rasterized. */
    public static void celtium$pointSize(float size) {
        GlsmHintLedger.current().noteArrival("glPointSize");
        CeltiumGlStub stub = installed;
        if (stub != null && size > 0.0f) {
            GlsmImmediateState.current().pointSize(size);
        }
    }

    /** {@code glGetObjectLabel/glGetObjectPtrLabel}: an empty label, written with its length. */
    public static void celtium$getObjectLabel(int bufSize, long length, long label) {
        if (length != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(length, 0);
        }
        if (label != 0L && bufSize > 0) {
            org.lwjgl.system.MemoryUtil.memPutByte(label, (byte) 0);
        }
    }

    /** Renderbuffer storage, recorded so its size queries answer the extent the caller asked for. */
    public static void celtium$bindRenderbuffer(int target, int renderbuffer) {
        GlsmHintLedger.current().noteArrival("glBindRenderbuffer");
        CeltiumGlStub stub = installed;
        if (stub != null) {
            GlsmClientArrayState.current().bindRenderbuffer(renderbuffer);
        }
    }

    public static void celtium$renderbufferStorage(int width, int height) {
        GlsmHintLedger.current().noteArrival("glRenderbufferStorage");
        CeltiumGlStub stub = installed;
        if (stub != null && GlsmClientArrayState.current().boundRenderbuffer() > 0 && width > 0 && height > 0) {
            GlsmClientArrayState.current().renderbufferStorage(
                    GlsmClientArrayState.current().boundRenderbuffer(), width, height);
        }
    }

    /** {@code glGetRenderbufferParameteriv}: width/height of the bound renderbuffer, zero otherwise. */
    public static void celtium$getRenderbufferParameter(int pname, long out) {
        CeltiumGlStub stub = installed;
        if (stub == null || out == 0L) {
            return;
        }
        int[] dims = GlsmClientArrayState.current().renderbufferSize(GlsmClientArrayState.current().boundRenderbuffer());
        int value = 0;
        if (dims != null) {
            if (pname == 0x8D42) {
                value = dims[0]; // GL_RENDERBUFFER_WIDTH
            } else if (pname == 0x8D43) {
                value = dims[1]; // GL_RENDERBUFFER_HEIGHT
            }
        }
        org.lwjgl.system.MemoryUtil.memPutInt(out, value);
    }

    /** {@code glGetNamedBufferParameteriv/i64v}: the DSA spelling of the size query. */
    public static void celtium$getNamedBufferParameter(int name, int pname, long out, boolean wide) {
        CeltiumGlStub stub = installed;
        if (stub == null || out == 0L) {
            return;
        }
        long size = 0L;
        try {
            NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
            if (nativeBuffer != null) {
                size = nativeBuffer.byteSize();
            } else {
                java.nio.ByteBuffer storage = GlsmBufferState.current().storage(name);
                size = storage == null ? 0L : stub.logicalSizeOf(name, storage.capacity());
            }
        } catch (RuntimeException error) {
            size = 0L;
        }
        long value = pname == 0x8764 ? size : 0L; // GL_BUFFER_SIZE
        if (wide) {
            org.lwjgl.system.MemoryUtil.memPutLong(out, value);
        } else {
            org.lwjgl.system.MemoryUtil.memPutInt(out, (int) value);
        }
    }

    /**
     * {@code glClearBufferData/SubData} and their named forms, for the zero fill only: a null clear value zeroes the
     * bytes whatever the internal format's component layout is, which is the case worth implementing without decoding
     * every format into a fill pattern. A non-zero value stays untouched.
     */
    public static void celtium$clearBuffer(int targetOrName, boolean named, long offset, long size, long data) {
        CeltiumGlStub stub = installed;
        if (stub == null || data != 0L) {
            return;
        }
        int name = named ? targetOrName : stub.boundBuffer(targetOrName);
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
                if (mapRangeOffset(offset, size, capacity) < 0L) {
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
        java.nio.ByteBuffer storage = GlsmBufferState.current().storage(name);
        if (storage == null) {
            return;
        }
        capacity = stub.logicalSizeOf(name, storage.capacity());
        if (offset < 0L || offset >= capacity) {
            return;
        }
        if (size <= 0L) {
            size = capacity - offset;
        }
        if (mapRangeOffset(offset, size, capacity) < 0L) {
            return;
        }
        org.lwjgl.system.MemoryUtil.memSet(org.lwjgl.system.MemoryUtil.memAddress(storage) + offset, 0, size);
    }

    /** {@code glLineWidth} from the transformer's route: the requested width the narrowing report compares against. */
    public static void celtium$lineWidth(float width) {
        GlsmHintLedger.current().noteArrival("glLineWidth");
        CeltiumGlStub stub = installed;
        if (stub != null && width > 0.0f) {
            GlsmImmediateState.current().lineWidth(width);
        }
    }

    /** {@code glPixelStorei} from the transformer's route: the unpack state a captured upload is read with. */
    public static void celtium$pixelStorei(int pname, int value) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.pixelStore(pname, value);
        }
    }

    /** The server-side capabilities a legacy push/pop is expected to restore. */
    private static final int[] ATTRIB_CAPABILITIES = {
            0x0BE2, // GL_BLEND
            0x0B44, // GL_CULL_FACE
            0x0B71, // GL_DEPTH_TEST
            0x0BD0, // GL_DITHER
            0x0B60, // GL_FOG
            0x0B50, // GL_LIGHTING
            0x0BC0, // GL_ALPHA_TEST
            0x0C11, // GL_SCISSOR_TEST
            0x8037, // GL_POLYGON_OFFSET_FILL
            0x0DE1, // GL_TEXTURE_2D
            0x0B57, // GL_COLOR_MATERIAL
            0x0B90, // GL_STENCIL_TEST
            0x0B41, // GL_POLYGON_SMOOTH
            0x809D, // GL_MULTISAMPLE
    };

    /**
     * {@code glPushAttrib}: snapshot every state group this renderer can put back, and remember the mask that says
     * which of them the caller asked for.
     *
     * <p>The push reads the <em>live</em> value from whichever authority owns it - the raster mirror for blending, the
     * colour write mask and culling, the backend's state mirror for the depth and alpha-test comparisons, this stub for
     * the colour, the line and point sizes, the list base and the texture binding. Reading a copy of a copy is how the
     * old stack ended up restoring "the last value the stub saw" rather than "the value in force"; the two disagree
     * whenever the game's own {@code GlStateManager} cache skips the GL call (measured on the depth test and the alpha
     * test, both of which the backend mirror is the authority for).
     *
     * <p>Bounded: a caller that pushes without popping (a mod's exception path) would otherwise grow this for the life
     * of the process. Past {@link #MAX_ATTRIB_DEPTH} entries the push is dropped and counted, so the pop restores an
     * older snapshot rather than nothing.
     */
    public static void celtium$pushAttrib(int mask) {
        GlsmHintLedger.current().noteArrival("glPushAttrib");
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        int caps = ATTRIB_CAPABILITIES.length;
        LegacyAttribSnapshot snapshot = new LegacyAttribSnapshot(caps);
        snapshot.mask = mask;
        for (int i = 0; i < caps; i++) {
            snapshot.capabilities[i] = stub.enabledCap(ATTRIB_CAPABILITIES[i]);
        }
        snapshot.red = GlsmImmediateState.current().red();
        snapshot.green = GlsmImmediateState.current().green();
        snapshot.blue = GlsmImmediateState.current().blue();
        snapshot.alpha = GlsmImmediateState.current().alpha();
        snapshot.lineWidth = GlsmImmediateState.current().lineWidth();
        snapshot.pointSize = GlsmImmediateState.current().pointSize();
        snapshot.depthWrites = stub.attribDepthWrites();
        snapshot.depthFunc = stub.attribDepthFunc();
        snapshot.alphaFunc = stub.attribAlphaFunc();
        snapshot.alphaReference = stub.attribAlphaReference();
        snapshot.blendSourceRgb = stub.raster.blendSrcRgb();
        snapshot.blendDestinationRgb = stub.raster.blendDstRgb();
        snapshot.blendSourceAlpha = stub.raster.blendSrcAlpha();
        snapshot.blendDestinationAlpha = stub.raster.blendDstAlpha();
        snapshot.blendEquationRgb = stub.raster.blendEquationRgb();
        snapshot.blendEquationAlpha = stub.raster.blendEquationAlpha();
        snapshot.colorMask = stub.raster.colorMask();
        snapshot.cullFace = stub.raster.cullFace();
        snapshot.frontFace = stub.raster.frontFace();
        snapshot.scissorX = stub.raster.scissorX();
        snapshot.scissorY = stub.raster.scissorY();
        snapshot.scissorWidth = stub.raster.scissorWidth();
        snapshot.scissorHeight = stub.raster.scissorHeight();
        snapshot.polygonOffsetFactor = stub.raster.polygonOffsetFactor();
        snapshot.polygonOffsetUnits = stub.raster.polygonOffsetUnits();
        snapshot.viewportX = GlsmViewportState.current().x();
        snapshot.viewportY = GlsmViewportState.current().y();
        snapshot.viewportWidth = GlsmViewportState.current().width();
        snapshot.viewportHeight = GlsmViewportState.current().height();
        snapshot.listBase = GlsmDisplayListLedger.current().listBase();
        snapshot.activeTextureUnit = stub.textures.activeUnit();
        snapshot.texture2d = stub.textures.texture2d();
        // GL_LIGHTING_BIT's restorable part. The authority is the backend's immediate-state mirror, which is where the
        // game's GlStateManager path and the raw glLightfv route both write; this stub keeps no second copy, and
        // reading one would be the "restore a copy of a copy" shape the rest of this push avoids.
        org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend lighting = vulkanBackend();
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

    /**
     * {@code glPopAttrib}: put back the groups the corresponding push saved.
     *
     * <p>The rule is GL's: a group is restored when the <em>pushed</em> mask selected it. {@code GL_ENABLE_BIT} alone
     * therefore restores the capability bits and leaves a {@code glColorMask} written inside the block in force - which
     * is what a mod that mirrors GL depends on, and what the old "restore everything" made impossible to see.
     * This mask rule is unconditional: the former A/B switch that restored every modelled group regardless of the
     * mask was removed once the mask-selective behaviour was adopted (item 396).
     */
    public static void celtium$popAttrib() {
        GlsmHintLedger.current().noteArrival("glPopAttrib");
        CeltiumGlStub stub = installed;
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

    /** The group-gated restore itself, beside the push so "saved here" and "put back there" cannot drift apart. */
    private void restoreAttrib(LegacyAttribSnapshot snapshot) {
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
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
            GlsmEnableState.current().set(GlStateQueries.GL_DEPTH_WRITEMASK_CAP, snapshot.depthWrites);
            if (backend != null) {
                backend.setDepthMask(snapshot.depthWrites);
                backend.depthFunc(snapshot.depthFunc);
            }
            restored |= LegacyAttribSnapshot.DEPTH_BUFFER_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.COLOR_BUFFER_BIT)) {
            // GL puts the blend factors and equation, the colour write mask and the alpha test's comparison in
            // GL_COLOR_BUFFER_BIT - the *enables* are GL_ENABLE_BIT, which is why this is a separate group.
            this.raster.blendFunc(snapshot.blendSourceRgb, snapshot.blendDestinationRgb,
                    snapshot.blendSourceAlpha, snapshot.blendDestinationAlpha);
            this.raster.blendEquation(snapshot.blendEquationRgb, snapshot.blendEquationAlpha);
            this.raster.colorMask(snapshot.colorMask);
            this.attribAlphaFunc = snapshot.alphaFunc;
            this.attribAlphaReference = snapshot.alphaReference;
            if (backend != null) {
                backend.setAlphaFunc(snapshot.alphaFunc, snapshot.alphaReference);
            }
            restored |= LegacyAttribSnapshot.COLOR_BUFFER_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.POLYGON_BIT)) {
            this.raster.cullFace(snapshot.cullFace);
            this.raster.frontFace(snapshot.frontFace);
            this.raster.polygonOffset(snapshot.polygonOffsetFactor, snapshot.polygonOffsetUnits);
            restored |= LegacyAttribSnapshot.POLYGON_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.SCISSOR_BIT)) {
            this.raster.scissor(snapshot.scissorX, snapshot.scissorY,
                    snapshot.scissorWidth, snapshot.scissorHeight);
            restored |= LegacyAttribSnapshot.SCISSOR_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.VIEWPORT_BIT)) {
            // Through the backend when there is one: it owns the group key's viewport term and the live sizes the
            // offers outside the full-target contract are refused against, and its own viewport writer updates the
            // packed rectangle and this stub's fields. Without a backend the packed value is still what the capture
            // reads, so it is written directly rather than left describing the popped block's rectangle.
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                vulkan.viewport(snapshot.viewportX, snapshot.viewportY,
                        snapshot.viewportWidth, snapshot.viewportHeight);
            } else {
                celtium$viewport(snapshot.viewportX, snapshot.viewportY,
                        snapshot.viewportWidth, snapshot.viewportHeight);
            }
            restored |= LegacyAttribSnapshot.VIEWPORT_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.LIST_BIT)) {
            GlsmDisplayListLedger.current().listBase(snapshot.listBase);
            restored |= LegacyAttribSnapshot.LIST_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.TEXTURE_BIT)) {
            this.textures.activeUnit(snapshot.activeTextureUnit);
            this.textures.bindTexture2d(snapshot.texture2d);
            restored |= LegacyAttribSnapshot.TEXTURE_BIT;
        }
        if (restoresAttrib(snapshot, LegacyAttribSnapshot.LIGHTING_BIT) && snapshot.lightingCaptured
                && backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend lighting) {
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

    /** The pushes a pop could not match, and the pushes dropped for depth - both visible in the census. */
    private long attribUnmatchedPops;
    private long attribOverflow;
    private long attribPushes;
    private long attribPops;
    private long attribRestoredGroups;
    private long attribUnrestoredGroups;
    /** Pops that selected {@code GL_LIGHTING_BIT}, and of those how many actually moved a value. */
    private long attribLightingRestores;
    private long attribLightingRestoresChanged;
    /** Pops that selected a group restored only in part; see {@code LegacyAttribSnapshot.PARTIAL_GROUPS}. */
    private long attribPartialGroups;
    /** The mask of the last push and the groups its pop restored, for the one-line census. */
    private int lastAttribMask;
    private int lastAttribRestored;
    /** Bounded histogram of the masks callers push: which groups the pack actually asks to be saved. */
    private final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap attribMasks =
            new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
    private static final int MAX_ATTRIB_MASKS = 8;
    /** {@code GL_DEPTH_WRITEMASK}, {@code GL_DEPTH_FUNC} and the alpha test as this stub last heard them. */
    private volatile boolean attribDepthWrites = true;
    volatile int attribDepthFunc = 0x0201;
    volatile int attribAlphaFunc = 0x0207;
    volatile float attribAlphaReference;

    /** The stub's record of a raw or managed {@code glDepthMask}, for the push snapshot and the query fallback. */
    public static void celtium$noteDepthMask(boolean mask) {
        GlsmHintLedger.current().noteArrival("glDepthMask");
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.attribDepthWrites = mask;
            GlsmEnableState.current().set(GlStateQueries.GL_DEPTH_WRITEMASK_CAP, mask);
        }
    }

    /** The stub's record of a raw or managed {@code glDepthFunc}. */
    public static void celtium$noteDepthFunc(int func) {
        GlsmHintLedger.current().noteArrival("glDepthFunc");
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.attribDepthFunc = func;
        }
    }

    /** The stub's record of a raw or managed {@code glAlphaFunc}. */
    public static void celtium$noteAlphaFunc(int func, float reference) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.attribAlphaFunc = func;
            stub.attribAlphaReference = reference;
        }
    }

    boolean attribDepthWrites() {
        var vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$depthWritesLive() : this.attribDepthWrites;
    }

    private int attribDepthFunc() {
        var vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$depthFuncGl() : this.attribDepthFunc;
    }

    private int attribAlphaFunc() {
        var vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$alphaTestFuncGl() : this.attribAlphaFunc;
    }

    private float attribAlphaReference() {
        var vulkan = vulkanBackend();
        return vulkan != null ? vulkan.celtium$alphaTestReference() : this.attribAlphaReference;
    }

    private void noteAttribMask(int mask) {
        Long previous = this.attribMasks.get(mask);
        if (previous != null) {
            this.attribMasks.put(mask, previous.longValue() + 1L);
        } else if (this.attribMasks.size() < MAX_ATTRIB_MASKS) {
            this.attribMasks.put(mask, 1L);
        }
    }

    /** {@return one line: how the pack pushes and pops attributes, and which selected groups went unrestored}. */
    public static String attribCensus() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "absent";
        }
        StringBuilder line = new StringBuilder();
        line.append("masks=").append(true)
                .append(" pushes=").append(stub.attribPushes)
                .append(" pops=").append(stub.attribPops)
                .append(" unmatchedPops=").append(stub.attribUnmatchedPops)
                .append(" overflow=").append(stub.attribOverflow)
                .append(" restoredGroups=").append(stub.attribRestoredGroups)
                .append(" selectedUnrestoredGroups=").append(stub.attribUnrestoredGroups)
                .append(" selectedPartialGroups=").append(stub.attribPartialGroups)
                .append(" lightingRestores=").append(stub.attribLightingRestores)
                .append(" lightingRestoresChanged=").append(stub.attribLightingRestoresChanged)
                .append(" last=0x").append(Integer.toHexString(stub.lastAttribMask))
                .append("/0x").append(Integer.toHexString(stub.lastAttribRestored))
                .append(" histogram=[");
        boolean first = true;
        for (it.unimi.dsi.fastutil.ints.Int2LongMap.Entry entry : stub.attribMasks.int2LongEntrySet()) {
            if (!first) {
                line.append(' ');
            }
            first = false;
            line.append("0x").append(Integer.toHexString(entry.getIntKey())).append(':').append(entry.getLongValue());
        }
        return line.append(']').toString();
    }

    // GAP-ST-6 rewired: both attribute stacks are owned by GlsmAttribStacks (same bound).
    /** GL's own stack has no documented limit; this one is bounded so an unbalanced mod cannot grow it forever. */
    private static final int MAX_ATTRIB_DEPTH = 64;

    /**
     * {@code glPushClientAttrib/glPopClientAttrib} from the transformer's route. Legacy mods save the client vertex-array
     * pointer and the GL_VERTEX_ARRAY enable around their own draws; a no-op push made the pop restore nothing, so the
     * next vanilla draw decoded with the mod's pointer.
     */
    public static void celtium$pushClientAttrib() {
        GlsmHintLedger.current().noteArrival("glPushClientAttrib");
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        GlsmAttribStacks.current().pushClient(new long[] {VERTEX_POINTER.size, VERTEX_POINTER.type,
                VERTEX_POINTER.stride, VERTEX_POINTER.offset, GlsmEnableState.current().enabled(0x8074) ? 1 : 0,
                VERTEX_POINTER.normalized ? 1L : 0L, VERTEX_POINTER.integer ? 1L : 0L});
    }

    public static void celtium$popClientAttrib() {
        GlsmHintLedger.current().noteArrival("glPopClientAttrib");
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        long[] saved = GlsmAttribStacks.current().popClient();
        if (saved == null) {
            return;
        }
        VERTEX_POINTER.size = (int) saved[0];
        VERTEX_POINTER.type = (int) saved[1];
        VERTEX_POINTER.stride = (int) saved[2];
        VERTEX_POINTER.offset = saved[3];
        GlsmEnableState.current().set(0x8074, saved[4] != 0);
        VERTEX_POINTER.normalized = saved.length > 5 && saved[5] != 0L;
        VERTEX_POINTER.integer = saved.length > 6 && saved[6] != 0L;
        // The restored pointer is attribute 0's record too; the two views of it must not drift apart.
        GlsmClientArrayState.VaoAttrib attrib = stub.boundAttribs()[0];
        attrib.size = VERTEX_POINTER.size;
        attrib.type = VERTEX_POINTER.type;
        attrib.stride = VERTEX_POINTER.stride;
        attrib.offset = VERTEX_POINTER.offset;
        attrib.normalized = VERTEX_POINTER.normalized;
        attrib.integer = VERTEX_POINTER.integer;
        attrib.pointerSet = true;
    }


    /** {@return the name bound to a texture target on the active unit, or zero when unbound}. */
    int textureBindingOrZero(int target) {
        int bound = target == GL_TEXTURE_2D ? this.textures.texture2d() : this.textureBindings.get(texBindKey(target));
        return bound < 0 ? 0 : bound;
    }

    // GAP-ST-8: the bound renderbuffer and its recorded extent are GlsmClientArrayState's too.

    /** The generic vertex attributes one VAO record carries; the owner's constant is the single source. */
    private static final int MAX_VERTEX_ATTRIBS = GlsmClientArrayState.MAX_VERTEX_ATTRIBS;

    /**
     * {@return the bound VAO's attribute records}, creating the VAO's array on first use.
     * The records themselves - the per-VAO arrays of {@link GlsmClientArrayState.VaoAttrib} -
     * are owned by {@link GlsmClientArrayState} (GLSM stage 3, GAP-ST-8 remainder); this
     * accessor only names which VAO is bound here.
     */
    private GlsmClientArrayState.VaoAttrib[] boundAttribs() {
        return GlsmClientArrayState.current().attribsFor(this.boundVertexArray);
    }

    /** Copies attribute 0's record into the legacy single-pointer fields the client-array paths read. */
    private void celtium$noteAttribZero() {
        GlsmClientArrayState.VaoAttrib position = boundAttribs()[0];
        VERTEX_POINTER.size = position.size;
        VERTEX_POINTER.type = position.type;
        VERTEX_POINTER.stride = position.stride;
        VERTEX_POINTER.offset = position.offset;
        VERTEX_POINTER.normalized = position.normalized;
        VERTEX_POINTER.integer = position.integer;
    }

    /** {@code glVertexAttribIPointer}: recorded per attribute like the float form; the decode refuses integer types. */
    public static void celtium$vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        CeltiumGlStub stub = installed;
        if (stub != null && index >= 0 && index < MAX_VERTEX_ATTRIBS) {
            GlsmClientArrayState.VaoAttrib attrib = stub.boundAttribs()[index];
            attrib.size = size;
            attrib.type = type;
            attrib.stride = stride;
            attrib.offset = pointer;
            attrib.normalized = false;
            attrib.integer = true;
            attrib.pointerSet = true;
            // The pointer is an offset into the array buffer bound at this moment; that association is VAO state
            // and has to be captured here, because the caller unbinds the buffer again before drawing.
            attrib.buffer = NativeGeometryBuffer.boundId();
            if (index == 0) {
                stub.celtium$noteAttribZero();
            }
        }
    }

    /** @code glVertexAttribPointer}: recorded per attribute in the bound VAO; index 0 is the position pointer. */
    public static void celtium$vertexAttribPointer(int index, int size, int type, boolean normalized, int stride,
            long pointer) {
        CeltiumGlStub stub = installed;
        if (stub != null && index >= 0 && index < MAX_VERTEX_ATTRIBS) {
            GlsmClientArrayState.VaoAttrib attrib = stub.boundAttribs()[index];
            attrib.size = size;
            attrib.type = type;
            attrib.stride = stride;
            attrib.offset = pointer;
            attrib.normalized = normalized;
            attrib.integer = false;
            attrib.pointerSet = true;
            attrib.buffer = NativeGeometryBuffer.boundId();
            if (index == 0) {
                stub.celtium$noteAttribZero();
            }
        }
    }

    /**
     * {@code glEnableVertexAttribArray}/{@code glDisableVertexAttribArray} from the transformer's route: the
     * enable is VAO state, kept per attribute. Index 0 also feeds the capability record the draw path has always
     * read for the position attribute, so the two spellings of that one fact cannot drift apart.
     */
    public static void celtium$vertexAttribEnabled(int index, boolean enabled) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        if (index >= 0 && index < MAX_VERTEX_ATTRIBS) {
            stub.boundAttribs()[index].enabled = enabled;
        }
        if (index == 0) {
            stub.setCapability(0x8622, enabled);
        }
    }

    /** Whether the generic attribute pointer asked for normalization, and whether it is an integer attribute. */

    /** Records the client vertex-array pointer, as the stub's own {@code glVertexPointer} case does. */
    public static void celtium$vertexPointer(int size, int type, int stride, long pointer) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            VERTEX_POINTER.size = size;
            VERTEX_POINTER.type = type;
            VERTEX_POINTER.stride = stride;
            VERTEX_POINTER.offset = pointer;
            // The legacy pointer is a float, unnormalized attribute: both generic flags reset with it.
            VERTEX_POINTER.normalized = false;
            VERTEX_POINTER.integer = false;
            // The same fact in the VAO's per-attribute record, so a VAO swap cannot resurrect an older pointer.
            GlsmClientArrayState.VaoAttrib attrib = stub.boundAttribs()[0];
            attrib.size = size;
            attrib.type = type;
            attrib.stride = stride;
            attrib.offset = pointer;
            attrib.normalized = false;
            attrib.integer = false;
            attrib.pointerSet = true;
            attrib.buffer = NativeGeometryBuffer.boundId();
        }
    }

    /**
     * {@code glDeleteSync}, from the transformer's route.
     * <p>
     * The generic delete dispatch already removes the id from the live set, but the transformer answered the native
     * with a zero, so a run logged 487 {@code glDeleteSync} calls as unmapped and the set only ever grew. The ids are
     * the stub's own (no native object exists), so releasing the bookkeeping is the whole operation.
     */
    public static void celtium$deleteSync(long sync) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            GlsmNameLedger.current().removeSync(sync);
        }
    }

    /**
     * Legacy immediate-mode capture: {@code glBegin}/{@code glEnd} with the current texture coordinate and colour.
     * <p>
     * Measured on a SuperSymmetry menu leg with the GL surface census: glVertex3f 6767, glTexCoord2d 5315, glBegin
     * 1692, glEnd 1691 and glTexCoord2f 968 calls were answered with a zero by the transformer - 82% of the unmapped
     * calls in the run, and every one of them a draw the picture lost (Forge's SplashProgress draws its loading bar
     * this way). The capture collects the vertices into the same POSITION_TEX_COLOR / POSITION_COLOR layouts the
     * client-array path uses and offers them at glEnd, so the matrices, texture and colour come from the mirrors the
     * other routes already maintain.
     * <p>
     * The packing itself lives in {@link LegacyImmediateCapture}, which has no GL or Vulkan dependency and is pinned by
     * a CPU test; this class owns the GL surface (which format the run gets, when a list compile owns the geometry) and
     * the one line each kind of refusal prints.
     */
    private final LegacyImmediateCapture legacyCapture = new LegacyImmediateCapture();

    /**
     * Whether the renderer can draw a primitive. Asked of the drawer rather than listed here so the capture and the
     * flush accept the same modes - the list that used to live here (quads, triangles, fans) refused the strips and
     * lines {@code VulkanImmediateDrawer} already draws.
     */
    private static final LegacyImmediateCapture.ModeGate LEGACY_MODE_GATE =
            org.soutalab.celtium.vulkan.VulkanImmediateDrawer::refusalReason;

    /** One bounded line per kind of legacy refusal, so a silent loss cannot read as "nothing to draw". */
    private static final int LEGACY_REFUSAL_LOG_LIMIT = 4;
    private int legacyRefusalLogs;
    private int legacyModeRefusalsLogged;

    /**
     * How many distinct legacy runs the census prints, {@code -Dceltium.debug.legacyRuns=N}; 0 (the default) is off.
     * <p>
     * The refusal lines above are what a run normally carries, and their cap is why the title screen's missing
     * background could not be attributed: the loading phase had already spent the four lines before the menu drew. The
     * census deduplicates by shape, so it costs one line per kind of quad and answers whether that quad was written,
     * with which texture, and where it landed.
     */
    private static final int LEGACY_RUN_LOG_LIMIT = Integer.getInteger("celtium.debug.legacyRuns", 0);
    private final java.util.Set<String> legacyRunSignatures = new java.util.HashSet<String>();
    private int legacyRunLogs;

    /*
     * The three display-list rules below are unconditional (item 396 removed their A/B switches once each adopted
     * side was decided):
     *
     * - {@code glBegin} geometry inside a {@code glNewList} compile is filed with the list. A refused run is a list
     *   that replays nothing, and GregTech's tool rotation marker - a {@code GL_COMPILE} list of
     *   {@code GL_LINE_LOOP} vertices, called every frame while a wrench is held - is drawn by exactly this route.
     *   Written from its class file, not from a guess: {@code gregtech/common/ToolEventHandlers.drawRotationMarker}
     *   calls {@code glNewList(4864)} (4864 is {@code GL_COMPILE}), {@code glBegin(GL_LINE_LOOP)},
     *   121 {@code glVertex3d}, {@code glEnd}, {@code glEndList}, and {@code glCallList} on every frame after that.
     * - A compiled legacy run takes an attribute the list never set from the state current at {@code glCallList},
     *   which is GL's rule for a display list, rather than the bytes baked at compile time. Baking is a wrong
     *   picture and not a smaller one: a mod that compiles a mesh once and draws it in a different colour per
     *   instance gets the compile-time colour under the baked rule, and OpenComputers' screen text, AE-Net-Vis'
     *   link renderers and GregTech's marker all set the colour immediately before {@code glCallList}.
     * - A compiled legacy run's vertex layout is chosen from the texturing state at {@code glCallList} rather than
     *   fixed at compile time. GL selects the layout when the vertex is executed, so a list compiled with
     *   {@code GL_TEXTURE_2D} enabled and called with it disabled draws untextured geometry carrying the colour it
     *   stored, and the reverse draws textured geometry with the caller's current coordinate. Replaying the
     *   compile-time layout instead samples whatever texture the caller had bound - or leaves the caller's texture
     *   unsampled - which is a wrong picture. Both directions are measured by
     *   {@code -Dceltium.validation.screen=displaylist}.
     */

    /**
     * The most bytes the stub will hold for compiled legacy runs across every list.
     * <p>
     * Bounded because a mod is entitled to compile a large list and recompile it: the copies are released with their
     * list ({@link #releaseListPayloads}), so this is the ceiling on what is live at once, not a total over the run.
     */
    private static final long MAX_LEGACY_LIST_RUN_BYTES = 64L * 1024L * 1024L;

    /** One line per distinct compiled run shape, so a list that replays nothing names what it held. */
    private static final int LEGACY_LIST_RUN_LOG_LIMIT = 8;
    private final java.util.Set<String> legacyListRunSignatures = new java.util.HashSet<String>();
    private int legacyListRunLogs;

    /** Compiled legacy runs: how many were filed, how many vertices, and how many were replayed. */
    private long legacyListRunsRecorded;
    private long legacyListRunVertices;
    private long legacyListRunsReplayed;
    private long legacyListRunReplayedVertices;
    /** Bytes currently held for compiled runs, and the part of it not yet published to a list name. */
    private long legacyListRunBytes;
    private long openListLegacyRunBytes;
    /** Bytes that arrived after the bound was reached, and runs that could not be copied at all. */
    private long legacyListRunBytesDropped;
    private long legacyListRunReplayRefusals;
    /** Replays that substituted the state current at glCallList into an attribute the list never set. */
    private long legacyListRunsCallState;
    /** Replays whose vertex layout was repacked because the texturing state at glCallList differed from the compile's. */
    private long legacyListRunsLayout;
    /** Bytes held per compiled list name, so a replaced or deleted list gives its allowance back. */
    private final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap legacyListRunBytesByList =
            new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

    /** {@code glBegin(mode)}: starts collecting; a previous run that was never ended is refused by name. */
    public static void celtium$legacyBegin(int mode) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        boolean textured = backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                && vulkan.celtium$texture2dEnabled();
        long nested = stub.legacyCapture.nestedRefusals();
        long lists = stub.legacyCapture.listRefusals();
        long modes = stub.legacyCapture.modeRefusals();
        stub.legacyCapture.begin(mode, textured, GlsmDisplayListLedger.current().compiling() != 0, LEGACY_MODE_GATE);
        if (stub.legacyCapture.nestedRefusals() != nested && stub.legacyRefusalLogs < LEGACY_REFUSAL_LOG_LIMIT) {
            stub.legacyRefusalLogs++;
            System.out.println("[Celtium] CELTIUM_LEGACY_REFUSED mode=" + mode + " reason=a second glBegin before glEnd");
        }
        if (stub.legacyCapture.listRefusals() != lists && stub.legacyRefusalLogs < LEGACY_REFUSAL_LOG_LIMIT) {
            stub.legacyRefusalLogs++;
            System.out.println("[Celtium] CELTIUM_LEGACY_REFUSED mode=" + mode
                    + " reason=a display-list run could not be recorded (list " + GlsmDisplayListLedger.current().compiling() + ")");
        }
        if (stub.legacyCapture.modeRefusals() != modes && stub.legacyModeRefusalsLogged < LEGACY_REFUSAL_LOG_LIMIT) {
            stub.legacyModeRefusalsLogged++;
            System.out.println("[Celtium] CELTIUM_LEGACY_MODE_REFUSED mode=" + mode
                    + " reason=" + stub.legacyCapture.lastRefusal());
        }
    }

    /** {@code glVertex2f}: the two-component spelling a mod's flat geometry uses. */
    public static void celtium$legacyVertex2f(float x, float y) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex2f(x, y);
        }
    }

    /** {@code glVertex2d}, narrowed to the float layout. */
    public static void celtium$legacyVertex2d(double x, double y) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex2d(x, y);
        }
    }

    /** {@code glVertex3f}: appends one vertex with the current texture coordinate and colour. */
    public static void celtium$legacyVertex3f(float x, float y, float z) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex3f(x, y, z);
        }
    }

    /** {@code glVertex3d}, narrowed to the float layout. */
    public static void celtium$legacyVertex3d(double x, double y, double z) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex3d(x, y, z);
        }
    }

    /** {@code glVertex4f}: the position is projected by {@code w}, as GL does. */
    public static void celtium$legacyVertex4f(float x, float y, float z, float w) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex4f(x, y, z, w);
        }
    }

    /** {@code glVertex4d}, narrowed before the projection. */
    public static void celtium$legacyVertex4d(double x, double y, double z, double w) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex4d(x, y, z, w);
        }
    }

    /** {@code glVertex2i}: the integer spelling, narrowed to the float layout as GL does. */
    public static void celtium$legacyVertex2i(int x, int y) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex2i(x, y);
        }
    }

    /** {@code glVertex3i}. */
    public static void celtium$legacyVertex3i(int x, int y, int z) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex3i(x, y, z);
        }
    }

    /** {@code glVertex4i}: the integer position is projected by {@code w}, as the float spelling is. */
    public static void celtium$legacyVertex4i(int x, int y, int z, int w) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex4i(x, y, z, w);
        }
    }

    /**
     * {@code glVertex2s}/{@code glVertex3s}: the short spellings.
     * <p>
     * A {@code (SS)V} descriptor has int locals holding sign-extended shorts, so both spellings land on the integer
     * entry point instead of a second narrowing step. They are separate methods because the transformer emits the
     * call by name, and a leg reads which spelling a mod actually used.
     */
    public static void celtium$legacyVertex2s(int x, int y) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex2i(x, y);
        }
    }

    /** {@code glVertex3s}. */
    public static void celtium$legacyVertex3s(int x, int y, int z) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.vertex3i(x, y, z);
        }
    }

    /** {@code glTexCoord2f}: the coordinate the following vertices carry. */
    public static void celtium$legacyTexCoord2f(float u, float v) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.texCoord(u, v);
        }
    }

    /** {@code glTexCoord2d}, narrowed to the float coordinate the capture stores. */
    public static void celtium$legacyTexCoord2d(double u, double v) {
        celtium$legacyTexCoord2f((float) u, (float) v);
    }

    /** {@code glTexCoord2i}, narrowed like the double spelling. */
    public static void celtium$legacyTexCoord2i(int u, int v) {
        celtium$legacyTexCoord2f((float) u, (float) v);
    }

    /** {@code glTexCoord2s}: int locals holding sign-extended shorts, narrowed to the stored float coordinate. */
    public static void celtium$legacyTexCoord2s(int u, int v) {
        celtium$legacyTexCoord2f((float) u, (float) v);
    }

    /** {@code GL_QUADS}: the primitive {@code glRect} is defined to expand into. */
    private static final int LEGACY_RECT_MODE = 7;
    /** {@code GL_TEXTURE0}: the only unit the immediate layouts here carry a coordinate for. */
    private static final int LEGACY_TEXTURE0 = 0x84C0;

    /** {@code glRectf}: a complete quad asked for in one call, not a vertex spelling. */
    public static void celtium$legacyRectf(float x1, float y1, float x2, float y2) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            legacyRect(stub, x1, y1, x2, y2);
        }
    }

    /** {@code glRectd}, narrowed to the float layout the capture stores. */
    public static void celtium$legacyRectd(double x1, double y1, double x2, double y2) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            legacyRect(stub, (float) x1, (float) y1, (float) x2, (float) y2);
        }
    }

    /** {@code glRecti}: GL rounds the integer corners to float, which is what the layout carries. */
    public static void celtium$legacyRecti(int x1, int y1, int x2, int y2) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            legacyRect(stub, (float) x1, (float) y1, (float) x2, (float) y2);
        }
    }

    /** {@code glRects}: a {@code (SSSS)V} descriptor's locals are sign-extended ints, so this is the integer form. */
    public static void celtium$legacyRects(int x1, int y1, int x2, int y2) {
        celtium$legacyRecti(x1, y1, x2, y2);
    }

    /**
     * The one expansion every {@code glRect*} spelling reaches: GL defines the call as {@code Begin(QUADS)}, four
     * texcoord/vertex pairs and {@code End}, so it is recorded as a run of its own through the same begin/rect/end path
     * a mod's own {@code glBegin} uses - same layout choice, same mode gate, same sink.
     * <p>
     * The caller's own run is never disturbed: a {@code glRect} between {@code glBegin} and {@code glEnd} is an error in
     * GL, and it is counted by name rather than silently appended to a run it does not belong to.
     */
    private static void legacyRect(CeltiumGlStub stub, float x1, float y1, float x2, float y2) {
        if (stub.legacyCapture.active()) {
            stub.legacyCapture.noteRefusal("glRect inside a glBegin the capture already opened");
            if (stub.legacyRefusalLogs < LEGACY_REFUSAL_LOG_LIMIT) {
                stub.legacyRefusalLogs++;
                System.out.println("[Celtium] CELTIUM_LEGACY_REFUSED rect reason=a glBegin is already open");
            }
            return;
        }
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        boolean textured = backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                && vulkan.celtium$texture2dEnabled();
        // Only a run this call opened is filled: a begin that refused (a display-list compile, a mode the renderer has
        // no draw for) has already named its reason, and appending into nothing would replace that name with a second
        // one that says the wrong thing.
        if (stub.legacyCapture.begin(LEGACY_RECT_MODE, textured, GlsmDisplayListLedger.current().compiling() != 0, LEGACY_MODE_GATE)
                && stub.legacyCapture.rect(x1, y1, x2, y2)) {
            celtium$legacyEnd();
        }
    }

    /**
     * {@code glMultiTexCoord2f(unit, s, t)}: unit 0 is the coordinate the immediate layouts here carry, so it is the
     * same call as {@code glTexCoord2f}; any other unit has no consumer, and it is counted instead of being written into
     * the one pair - a unit-1 lightmap coordinate used as albedo UVs is a wrong picture, not a missing one.
     * <p>
     * The light map's own coordinate pair does not come through here: {@code LightmapCoordsMixin} carries it from
     * {@code OpenGlHelper.setLightmapTextureCoords}, which is why the transformer's default answer for this name was a
     * bare return.
     */
    public static void celtium$legacyMultiTexCoord2f(int unit, float u, float v) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        if (unit == LEGACY_TEXTURE0) {
            stub.legacyCapture.texCoordUnit0(u, v);
        } else {
            stub.legacyCapture.noteOtherTexUnit(unit);
        }
    }

    /**
     * One integer colour component as GL's {@code [0, 1]} float, read as an unsigned value of its own width.
     * <p>
     * This is the one conversion every integer {@code glColor*} spelling goes through, so the widths cannot disagree:
     * 255, 65535 and 0xFFFFFFFF are all 1.0, and half the range is 0.5. The reading is unsigned because a Java caller
     * writes these bit patterns without an unsigned type - {@code glColor4ub(r, g, b, (byte) 255)} is white, so
     * {@code glColor4us(..., (short) 65535)} and {@code glColor4ui(..., -1)} mean white too. GL's own signed
     * normalisation would read -1 as negative and clamp it to black, which is not what the call site meant and not
     * what the unsigned spelling next to it does.
     * <p>
     * The scale is the type's maximum (255, 65535, 2^32-1), which is also GL's factor for the unsigned spellings.
     */
    public static float celtium$colourComponent(byte value) {
        return (value & 0xFF) / 255.0F;
    }

    public static float celtium$colourComponent(short value) {
        return (value & 0xFFFF) / 65535.0F;
    }

    public static float celtium$colourComponent(int value) {
        // Through a long: the top bit of the int would otherwise make the division negative.
        return (float) ((value & 0xFFFFFFFFL) / 4294967295.0);
    }

    /** Records the colour for {@code glGetFloatv(GL_CURRENT_COLOR)}; fed by every path that sets the mirror. */
    public static void celtium$setCurrentColor(float red, float green, float blue, float alpha) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            GlsmImmediateState.current().color(red, green, blue, alpha);
        }
    }

    /** The raw colour routes' current colour, captured for legacy vertices (GL keeps one current colour). */
    public static void celtium$legacyColor(float red, float green, float blue, float alpha) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.legacyCapture.color(red, green, blue, alpha);
        }
    }

    /** {@code glEnd}: offers the collected run through the immediate path, or refuses it with a named reason. */
    public static void celtium$legacyEnd() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        int vertices = stub.legacyCapture.vertexCount();
        int mode = stub.legacyCapture.mode();
        int format = stub.legacyCapture.format();
        long sinkRefusals = stub.legacyCapture.sinkRefusals();
        // The run's placement, read before end() resets the buffer. Taken only when the census is on, so a normal leg
        // pays nothing for it.
        float[] firstPosition = null;
        float[] lastPosition = null;
        float[] firstUv = null;
        float[] lastUv = null;
        if (LEGACY_RUN_LOG_LIMIT > 0 && vertices > 0) {
            firstPosition = new float[3];
            lastPosition = new float[3];
            firstUv = new float[2];
            lastUv = new float[2];
            if (!stub.legacyCapture.vertexAt(0, firstPosition, firstUv)
                    || !stub.legacyCapture.vertexAt(vertices - 1, lastPosition, lastUv)) {
                firstPosition = null;
            }
        }
        boolean taken = stub.legacyCapture.end((address, stride, layout, count, drawMode) -> {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            boolean accepted = backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                    && vulkan.submitImmediateBatch(null, stride, layout, address, count, drawMode);
            if (!accepted) {
                stub.legacyCapture.noteRefusal("the immediate path refused it"
                        + (backend == null ? " (no backend)" : ": " + backend.lastRefusal()));
            }
            return accepted;
        }, (layout, stride, count, drawMode, address, attributes) ->
                stub.recordLegacyRunForList(layout, stride, count, drawMode, address, attributes));
        if (taken && stub.legacyCapture.submittedRuns() == 1) {
            System.out.println("[Celtium] CELTIUM_LEGACY_IMMEDIATE submitted=1 vertices=" + vertices + " mode=" + mode
                    + " format=" + format + " (legacy glBegin geometry now reaches the immediate path)");
        } else if (!taken && stub.legacyCapture.sinkRefusals() != sinkRefusals
                && stub.legacyRefusalLogs < LEGACY_REFUSAL_LOG_LIMIT) {
            stub.legacyRefusalLogs++;
            System.out.println("[Celtium] CELTIUM_LEGACY_REFUSED mode=" + mode + " reason="
                    + stub.legacyCapture.lastRefusal());
        }
        if (firstPosition != null) {
            stub.celtium$logLegacyRun(mode, vertices, format, taken, firstPosition, lastPosition, firstUv, lastUv);
        }
    }

    /**
     * Files one {@code glBegin}/{@code glEnd} run with the display list being compiled, as a copy the list replays.
     *
     * <p>Why the copy and the ordering matter is the same fact twice: GL compiles the commands and not the drawing, so
     * a run written inside {@code glNewList} has to be stored where {@code glCallList} can replay it - and it has to be
     * stored <em>in position</em>, because the commands around it (a {@code glTranslate}, a {@code glColor}, a
     * {@code glBindTexture}) are replayed in order and the run draws under the state they leave. The entry therefore
     * goes into the list's own command array, between the recorded calls that preceded and followed it.
     *
     * <p>The vertices are the capture's, which the next {@code glBegin} overwrites, so {@link CompiledLegacyRun} copies
     * them. The copy is registered as a list payload, which is what frees it when the list is replaced or deleted - the
     * one lifecycle the stub already enforces for compile-time buffers. The bytes held at once are bounded; past the
     * bound a run is dropped and counted rather than growing the process.
     *
     * @return whether the run was filed
     */
    private boolean recordLegacyRunForList(int format, int stride, int count, int drawMode, long address,
                                           int attributes) {
        List<Object[]> open = this.openList;
        if (open == null || GlsmDisplayListLedger.current().compiling() <= 0 || count <= 0) {
            return false;
        }
        long bytes = (long) count * (long) stride;
        if (this.legacyListRunBytes + bytes > MAX_LEGACY_LIST_RUN_BYTES) {
            this.legacyListRunBytesDropped += bytes;
            return false;
        }
        CompiledLegacyRun run = CompiledLegacyRun.copy(format, stride, count, drawMode, address, attributes);
        if (run == null) {
            this.legacyListRunBytesDropped += bytes;
            return false;
        }
        open.add(new Object[] { CompiledLegacyRun.TAG, run });
        if (this.openListPayloads == null) {
            this.openListPayloads = new ArrayList<java.nio.ByteBuffer>();
        }
        this.openListPayloads.add(run.buffer());
        this.legacyListRunBytes += bytes;
        this.openListLegacyRunBytes += bytes;
        this.legacyListRunsRecorded++;
        this.legacyListRunVertices += count;
        if (this.legacyListRunLogs < LEGACY_LIST_RUN_LOG_LIMIT) {
            String key = drawMode + "/" + count + "/" + format + "/" + attributes;
            if (this.legacyListRunSignatures.add(key)) {
                this.legacyListRunLogs++;
                System.out.println("[Celtium] CELTIUM_LEGACY_LIST_RUN list=" + GlsmDisplayListLedger.current().compiling() + " mode=" + drawMode
                        + " vertices=" + count + " format=" + format + " bytes=" + bytes + " attributes=" + attributes
                        + " (compiled into the list; glCallList replays it with the state current then)");
            }
        }
        return true;
    }

    /**
     * Replays one compiled legacy run: the same immediate path a run outside a list takes, under the matrix, texture and
     * colour state current at {@code glCallList}.
     *
     * <p>Drawing it any other way would break the two things a display list promises: that the geometry is stored once
     * and drawn where the caller put it, and that state changes made after the compile are not baked in. Both are why
     * the run carries no matrix - {@link CompiledLegacyRun} stores vertices and a primitive only.
     */
    private void replayLegacyRun(CompiledLegacyRun run) {
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        boolean vulkanBackend = backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend;
        // GL's rule, applied here because the copy is the run's vertices: an attribute the list never set is the
        // caller's current value, and the layout is the one the caller's texturing state selects. The commands the list
        // did record (a glColor inside the compile, a glTexCoord between two vertices) have already run through this
        // dispatch and left their answer in the capture's mirror - or in the baked bytes, which is why the capture marks
        // a run whose list changes an attribute between its vertices.
        CompiledLegacyRun.Submission submission;
        int substitutedBefore = run.callTimeColourReplays() + run.callTimeTexReplays();
        boolean texturedAtCall = vulkanBackend
                && ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) backend).celtium$texture2dEnabled();
        submission = run.prepareCall(true, texturedAtCall, this.legacyCapture.red(),
                this.legacyCapture.green(), this.legacyCapture.blue(), this.legacyCapture.alpha(),
                this.legacyCapture.texU(), this.legacyCapture.texV());
        if (submission.format != run.format) {
            this.legacyListRunsLayout++;
        }
        if (run.callTimeColourReplays() + run.callTimeTexReplays() != substitutedBefore) {
            this.legacyListRunsCallState++;
        }
        boolean taken = backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                && vulkan.submitImmediateBatch(null, submission.stride, submission.format, submission.address,
                        run.count, run.mode);
        if (taken) {
            this.legacyListRunsReplayed++;
            this.legacyListRunReplayedVertices += run.count;
            return;
        }
        this.legacyListRunReplayRefusals++;
        if (this.legacyRefusalLogs < LEGACY_REFUSAL_LOG_LIMIT) {
            this.legacyRefusalLogs++;
            System.out.println("[Celtium] CELTIUM_LEGACY_LIST_REPLAY_REFUSED list=" + GlsmDisplayListLedger.current().lastCalled()
                    + " " + run.describe() + " reason="
                    + (backend == null ? "no backend" : backend.lastRefusal()));
        }
    }

    /**
     * {@return the compiled legacy-run census}, for the verification line.
     * <p>
     * {@code recorded} counts the runs filed with a list and {@code replayed} the ones a {@code glCallList} actually
     * drew, because a list that was compiled and never called must not read as a drawing path: GregTech's marker is
     * compiled once and called every frame while a wrench is held, so the two numbers differ by design.
     */
    public static String legacyListCensus() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "none";
        }
        return "enabled=" + true
                + " recorded=" + stub.legacyListRunsRecorded + "/" + stub.legacyListRunVertices
                + " replayed=" + stub.legacyListRunsReplayed + "/" + stub.legacyListRunReplayedVertices
                + " lists=" + stub.legacyListRunBytesByList.size()
                + " bytes=" + stub.legacyListRunBytes + " droppedBytes=" + stub.legacyListRunBytesDropped
                + " replayRefusals=" + stub.legacyListRunReplayRefusals
                + " callState=" + stub.legacyListRunsCallState
                + " layout=" + stub.legacyListRunsLayout
                + " provenance[colour=" + stub.legacyCapture.compiledCallStateColour()
                + " tex=" + stub.legacyCapture.compiledCallStateTex()
                + " midRun=" + stub.legacyCapture.compiledMidRunChanges()
                + " rollbacks=" + stub.legacyCapture.compileRollbacks() + "]"
                + " callStateSwitch=" + true + " callLayoutSwitch=" + true;
    }

    /**
     * One bounded line per distinct legacy run, {@code -Dceltium.debug.legacyRuns=N} (default 0, off).
     * <p>     * Why a census and not a single event: the legacy path's refusals are capped at four lines per run, and the menu's
     * share of them was already spent by the splash before the title screen drew its first quad, so the picture's
     * missing background could not be attributed from the log. The title screen issues the same handful of quads every
     * frame - a black fill, a slideshow background, a banner, a frame, one per button - so deduplicating by shape and
     * bound texture costs one line each and answers the three questions the picture cannot: was the quad written, with
     * which texture, and where did it land in GL coordinates.
     * <p>
     * Each line carries the texture the run sampled with its mirrored size and whether that mirror is complete, the
     * run's two end vertices and their texture coordinates, whether the immediate path took it, and the mirrored
     * matrices the group will be transformed by. A quad that is accepted with a complete texture and on-screen
     * coordinates still missing from the frame then points at the sampling or the composite, not at the capture.
     */
    private void celtium$logLegacyRun(int mode, int vertices, int format, boolean taken,
                                      float[] firstPosition, float[] lastPosition, float[] firstUv, float[] lastUv) {
        String key = mode + "/" + vertices + "/" + format + "/" + this.textures.texture2d() + "/"
                + round2(firstPosition[0]) + "," + round2(firstPosition[1]) + "/"
                + round2(lastPosition[0]) + "," + round2(lastPosition[1]);
        if (!this.legacyRunSignatures.add(key) || this.legacyRunLogs >= LEGACY_RUN_LOG_LIMIT) {
            return;
        }
        this.legacyRunLogs++;
        int texture = this.textures.texture2d();
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        String mirror = "none";
        String matrix = "none";
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            mirror = vulkan.celtium$textureCensus(texture);
            matrix = vulkan.celtium$immediateMatrixState();
        }
        System.out.println("[Celtium] CELTIUM_LEGACY_RUN mode=" + mode + " vertices=" + vertices + " format=" + format
                + " texture=" + texture + " mirror=" + mirror + " taken=" + taken
                // The run's current colour: a quad accepted with a full texture but written with a zero alpha is the
                // one case where "drawn" and "visible" differ without any refusal to point at.
                + " colour=" + round2(this.legacyCapture.red()) + "," + round2(this.legacyCapture.green()) + ","
                + round2(this.legacyCapture.blue()) + "," + round2(this.legacyCapture.alpha())
                + " first=(" + round2(firstPosition[0]) + "," + round2(firstPosition[1]) + "," + round2(firstPosition[2])
                + " uv=" + round2(firstUv[0]) + "," + round2(firstUv[1]) + ")"
                + " last=(" + round2(lastPosition[0]) + "," + round2(lastPosition[1]) + "," + round2(lastPosition[2])
                + " uv=" + round2(lastUv[0]) + "," + round2(lastUv[1]) + ")"
                + " " + matrix
                + " refusal=" + (taken ? "none" : this.legacyCapture.lastRefusal()));
    }

    /** Rounds to two decimals for a log line, so a float's noise does not decide a reading. */
    private static String round2(float value) {
        return Float.toString(Math.round(value * 100.0f) / 100.0f);
    }

    /**
     * {@return the size the stub answers for a texture level query, or 0 when it has nothing recorded}.
     * <p>
     * GL answers {@code glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH/HEIGHT)} from the bound texture's
     * storage. The transformer answered a fixed 256 for every caller, which is right for the two callers that motivated
     * it (Forge's 256-square cloud texture and the 256-square light map) and wrong for every other texture that divides
     * by the answer. CustomMainMenu's title screen is that case: its background is a 1920x1080 slide drawn with
     * {@code MODE.FILL}, and the fill's cover rectangle and its texture coordinates are both computed from this query,
     * so a 256 answer asked a 1920x1080 image to be a 256x256 one.
     * <p>
     * The answer is the extent the uploads already recorded for that name at level 0 - the same record the DSA
     * {@code glGetTextureLevelParameteriv} route reads - so no new measurement is taken and a texture with no record
     * (never uploaded, a proxy target, a level above 0) keeps the hint's fallback.
     */
    public static int celtium$texLevelSize(int target, int level, int pname) {
        CeltiumGlStub stub = installed;
        if (stub == null || target != GL_TEXTURE_2D || level != 0) {
            return 0;
        }
        int[] extent = celtium$textureExtent(stub.textures.texture2d());
        return extent == null ? 0 : celtium$texLevelAnswer(extent[0], extent[1], pname);
    }

    /**
     * {@return the answer for a recorded extent and a level-query name, or 0 when the query is not one this answers}.
     * <p>
     * Split out from {@link #celtium$texLevelSize(int, int, int)} so the two names a caller divides by are decided in
     * one place that a plain JVM test can pin without a GL stub.
     */
    public static int celtium$texLevelAnswer(int width, int height, int pname) {
        return TextureLevelAnswer.answer(width, height, pname);
    }

    /**
     * Records the split texture-coordinate pointer, from the transformer's route.
     * <p>
     * These are the native calls the transformer used to drop: the pointer families it did not route reached
     * {@code emitDefaultReturn}, so the stub's fields stayed zero while a stride-48 draw was refused for "client arrays"
     * it could never see. The first version of the repack registered the names in the stub's table instead, which
     * changed nothing for the measured draw - the transformer is the route, and a table row alone does not install it.
     */
    public static void celtium$texCoordPointer(int size, int type, int stride, long pointer) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.texCoordPointerSize = size;
            stub.texCoordPointerType = type;
            stub.texCoordPointerStride = stride;
            stub.texCoordPointerOffset = pointer;
        }
    }

    /** Records the split colour pointer, from the transformer's route. */
    public static void celtium$colorPointer(int size, int type, int stride, long pointer) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.colorPointerSize = size;
            stub.colorPointerType = type;
            stub.colorPointerStride = stride;
            stub.colorPointerOffset = pointer;
        }
    }

    /** Records the split normal pointer, from the transformer's route; the normal is recorded but never drawn. */
    public static void celtium$normalPointer(int type, int stride, long pointer) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.normalPointerType = type;
            stub.normalPointerStride = stride;
            stub.normalPointerOffset = pointer;
        }
    }

    /**
     * The texture upload family, from the transformer's route.
     *
     * <p>These two calls are how every texture gets its pixels, and under GL-less they were rewritten to no-ops: the
     * stub's capture never saw an upload, so the registry answered {@code CELTIUM_CAPTURE_MISS ... the stub captured no
     * upload for this texture level} and the mirror stayed content-less. The block atlas is the measured case - the
     * 268-mod E2E pack's 8192x8192 atlas is GL texture 353, its mirror never became full, the terrain pass refused
     * every batch, and the lower half of the frame was drawn by nobody.
     */
    public static void celtium$texImage2D(int target, int level, int internal, int width, int height, int border,
            int format, int type, long pixels) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.texImagePixels("glTexImage2D", target, level, internal, width, height, border, format, type, pixels);
        }
    }

    /** Sub-image writes, the same producer the dispatch case uses. */
    public static void celtium$texSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height,
            int format, int type, long pixels) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.texSubImage(target, level, xOffset, yOffset, width, height, format, type, pixels);
        }
    }

    /** Records a raw framebuffer's colour attachment, so the renderer can resolve it as it resolves a vanilla one. */
    public static void celtium$framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.framebufferTexture2D(target, attachment, textarget, texture, level);
        }
    }

    /** Records which framebuffer is bound, which is the pair {@code glFramebufferTexture2D} files its record under. */
    public static void celtium$bindFramebuffer(int target, int name) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.bindFramebuffer(target, name);
        }
    }

    /** Mirrors an array buffer's bytes, as the stub's own {@code glBufferData} case does. */
    public static void celtium$bufferData(int target, long size, long address, int usage) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        stub.bufferDataFor(stub.boundBuffer(target), size, address);
    }

    /** {@return the last name {@code GlStateManager.bindTexture} named, or -1 when none was seen}. */
    public static int celtium$boundTexture() {
        CeltiumGlStub stub = installed;
        return stub == null ? -1 : stub.textures.texture2d();
    }

    /** GL_SCISSOR_TEST: the enable bit that says whether the recorded rectangle clips. */
    static final int GL_SCISSOR_TEST = 0x0C11;

    /**
     * GL_DEPTH_TEST: one of the capabilities a deferred draw can outlive. See the native state boundary.
     */
    static final int GL_DEPTH_TEST = 0x0B71;
    /** GL_ALPHA_TEST: the capability glAlphaFunc's comparison only applies while it is enabled. */
    static final int GL_ALPHA_TEST = 0x0BC0;

    /** Compatibility registration; the callback belongs to the native state owner. */
    public static void celtium$setDrawStateBoundaryHook(Runnable hook) {
        org.soutalab.celtium.vulkan.NativeRasterState.current().beforeChange(hook);
    }

    private void noteDrawStateBoundary() {
        this.raster.flushBeforeChange();
    }

    /** {@return whether {@code capability} is one whose record the group's capture can read or key a run by}. */
    private static boolean isDrawStateCapability(int capability) {
        return capability == GL_DEPTH_TEST || capability == GL_BLEND
                || capability == GL_CULL_FACE || capability == GL_SCISSOR_TEST;
    }

    /** GL_POLYGON_OFFSET_FILL: the enable bit that says whether the recorded offset applies to filled polygons. */
    static final int GL_POLYGON_OFFSET_FILL = 0x8037;

    /** glPolygonOffset's two arguments, in GL's own convention (factor first, then units). */
    /** How many glPolygonOffset calls this process made; 0 means the call never reached the stub. */
    private long polygonOffsetCalls;

    /** {@return GL's polygon offset factor}, 0 when glPolygonOffset has not been called. */
    public static float polygonOffsetFactor() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0.0F : stub.raster.polygonOffsetFactor();
    }

    /** {@return GL's polygon offset units}, 0 when glPolygonOffset has not been called. */
    public static float polygonOffsetUnits() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0.0F : stub.raster.polygonOffsetUnits();
    }

    /** {@return how many glPolygonOffset calls reached this stub}. */
    public static long polygonOffsetCallCount() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0L : stub.polygonOffsetCalls;
    }

    /** {@return whether glPolygonOffset has been called at all}: (0,0) is a legal offset, so the call is the fact. */
    public static boolean polygonOffsetCalled() {
        CeltiumGlStub stub = installed;
        return stub != null && stub.raster.polygonOffsetCalled();
    }

    /**
     * {@return whether GL_POLYGON_OFFSET_FILL is enabled}, read from the same {@code enables} map glEnable/glDisable
     * write so there is one state rather than a mirror that can drift from it.
     */
    public static boolean polygonOffsetFillEnabled() {
        CeltiumGlStub stub = installed;
        return stub != null
                && stub.raster.polygonOffsetFillEnabled();
    }
    // BH-08: the cull capability and the two face constants, spelled here the way this class spells GL_SCISSOR_TEST -
    // it deliberately carries no org.lwjgl.opengl import, so the numbers are named where they are used.
    static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_BACK = 0x0405;
    private static final int GL_CCW = 0x0901;

    /** The game's clip rectangle as glScissor last set it, in GL's bottom-left coordinates. */

    /**
     * Initialize the two context rectangles from the actual drawable, once at window creation.
     * Later viewport changes must not alter the independent scissor box.
     */
    public static void initializeWindowRectangles(int width, int height) {
        CeltiumGlStub stub = installed;
        if (stub == null || width <= 0 || height <= 0) return;
        GlsmViewportState.current().initialize(0, 0, width, height);
        stub.raster.initializeScissor(width, height);
    }

    /** {@return the last viewport rectangle packed into one long, or {@code -1} when the game never set one}. */
    public static long celtium$viewportPacked() {
        return GlsmViewportState.current().packed();
    }

    /**
     * Records the viewport selected through Minecraft's API or LWJGL3's native entry point.
     * Both entries that can set it go through GlsmViewportState: the transformed native's route
     * (this method, which the hint calls) and the stub's own dispatch table (SEM_VIEWPORT).
     */
    public static void celtium$viewport(int x, int y, int width, int height) {
        GlsmHintLedger.current().noteArrival("glViewport");
        GlsmViewportState.current().set(x, y, width, height);
    }

    /** glScissor calls seen, so a leg can say whether the scene clipped at all rather than only whether a group did. */
    private long scissorCallCount;

    /** GL_SCISSOR_TEST enable/disable calls seen, the other half of "did this scene clip". */
    private long scissorToggleCount;

    /** Whether glScissor has been called at all: a box of zeros is a legal clip, so the call is what is recorded. */

    /** The game's cull face and front winding, recorded by their semantic cases (BH-08). */
    private volatile boolean cullFaceCalled;
    private volatile boolean frontFaceCalled;

    /**
     * {@return whether GL_CULL_FACE is enabled}, read from the same {@code enables} map glEnable/glDisable write.
     * <p>
     * BH-08: the immediate state mirror has a cull flag and a cull face, and nothing on the game path ever set
     * either - the capability was recorded but never read, and {@code glCullFace} was a no-op - so every immediate
     * batch was built with culling off and the back faces of items, entities and mirror-scaled models stayed visible.
     */
    public static boolean cullEnabled() {
        CeltiumGlStub stub = installed;
        return stub != null
                && stub.raster.cullEnabled();
    }

    /** {@return the last {@code glCullFace} argument}, or {@code GL_BACK} when it has not been called. */
    public static int cullFace() {
        CeltiumGlStub stub = installed;
        return stub == null || !stub.cullFaceCalled ? GL_BACK : stub.raster.cullFace();
    }

    /** {@return the last {@code glFrontFace} argument}, or {@code GL_CCW} when it has not been called. */
    public static int frontFace() {
        CeltiumGlStub stub = installed;
        return stub == null || !stub.frontFaceCalled ? GL_CCW : stub.raster.frontFace();
    }

    // BH-07: the blend state, spelled the way this class spells the cull pair above - it deliberately carries no
    // org.lwjgl.opengl import, so the numbers are named where they are used. The initial values are *GL's own*
    // initial state, not an invented default: source ONE, destination ZERO (GL's black box starts there, and so does
    // GlStateManager's cache - read in the original, `BlendState` sets srcFactor = 1 / dstFactor = 0 / the alpha pair
    // likewise), equation FUNC_ADD and every colour channel writable. That matters because the game's own wrappers
    // are cached: a wrapper that never called GL leaves the real state at these values, and a mirror that started
    // somewhere else would report a blend the device is not applying.
    static final int GL_BLEND = 0x0BE2;
    /** {@code GL_FUNC_ADD}, the equation GL starts with and the only one the pipeline bakes besides the mapped set. */
    private static final int GL_FUNC_ADD = 0x8006;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;

    private volatile boolean blendFactorsCalled;
    private volatile boolean blendEquationCalled;
    /** Bit 0 red, 1 green, 2 blue, 3 alpha: the same nibble Vulkan's VK_COLOR_COMPONENT_*_BIT uses, spelled here. */
    private volatile boolean colorMaskCalled;

    /** Calls of each recorded entry point, so "the path was reached" is a number rather than an expectation. */
    private final java.util.concurrent.atomic.AtomicLong blendFuncCalls = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong blendSeparateCalls = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong blendEquationCalls = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong colorMaskCalls = new java.util.concurrent.atomic.AtomicLong();

    /** {@return whether GL_BLEND is enabled}, read from the same {@code enables} map glEnable/glDisable write. */
    public static boolean blendEnabled() {
        CeltiumGlStub stub = installed;
        return stub != null && stub.raster.blendEnabled();
    }

    public static boolean blendEnableRecorded() {
        CeltiumGlStub stub = installed;
        return stub != null && GlsmEnableState.current().observed(GL_BLEND);
    }

    public static boolean cullEnableRecorded() {
        CeltiumGlStub stub = installed;
        return stub != null && GlsmEnableState.current().observed(GL_CULL_FACE);
    }

    public static boolean cullFaceRecorded() {
        CeltiumGlStub stub = installed;
        return stub != null && stub.cullFaceCalled;
    }

    /** {@return the RGB source factor GL was last given}, or GL's initial {@code ONE} when it was never told. */
    public static int blendSrcRgb() {
        CeltiumGlStub stub = installed;
        return stub == null ? GL_SRC_ALPHA : stub.raster.blendSrcRgb();
    }

    /** {@return the RGB destination factor GL was last given}, or GL's initial {@code ZERO}. */
    public static int blendDstRgb() {
        CeltiumGlStub stub = installed;
        return stub == null ? GL_ONE_MINUS_SRC_ALPHA : stub.raster.blendDstRgb();
    }

    /** {@return the alpha source factor GL was last given}, or GL's initial {@code ONE}. */
    public static int blendSrcAlpha() {
        CeltiumGlStub stub = installed;
        return stub == null ? 1 : stub.raster.blendSrcAlpha();
    }

    /** {@return the alpha destination factor GL was last given}, or GL's initial {@code ZERO}. */
    public static int blendDstAlpha() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : stub.raster.blendDstAlpha();
    }

    /** {@return the RGB blend equation GL was last given}, or GL's initial {@code GL_FUNC_ADD}. */
    public static int blendEquationRgb() {
        CeltiumGlStub stub = installed;
        return stub == null ? GL_FUNC_ADD : stub.raster.blendEquationRgb();
    }

    /** {@return the alpha blend equation GL was last given}, or GL's initial {@code GL_FUNC_ADD}. */
    public static int blendEquationAlpha() {
        CeltiumGlStub stub = installed;
        return stub == null ? GL_FUNC_ADD : stub.raster.blendEquationAlpha();
    }

    /**
     * {@return which colour channels writes may touch}, as the Vulkan {@code VK_COLOR_COMPONENT_*} nibble (bit 0
     * red, 1 green, 2 blue, 3 alpha). GL's own initial state is all four, which is the value before any call.
     */
    public static int colorMask() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0xF : stub.raster.colorMask();
    }

    /** {@return whether any of the two blend-factor entry points was called at all}. */
    public static boolean blendFactorsRecorded() {
        CeltiumGlStub stub = installed;
        return stub != null && stub.blendFactorsCalled;
    }

    /** {@return whether either blend-equation entry point was called at all}. */
    public static boolean blendEquationRecorded() {
        CeltiumGlStub stub = installed;
        return stub != null && stub.blendEquationCalled;
    }

    /** {@return whether glColorMask was called at all}: (true,true,true,true) is a legal call, so the call is the fact. */
    public static boolean colorMaskRecorded() {
        CeltiumGlStub stub = installed;
        return stub != null && stub.colorMaskCalled;
    }

    /** {@return a one-line census of the blend/mask entry points that were reached}, for the exit line. */
    public static String blendCensusSummary() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "not-installed";
        }
        return "blendFuncCalls=" + stub.blendFuncCalls.get() + " blendFuncSeparateCalls="
                + stub.blendSeparateCalls.get() + " blendEquationCalls=" + stub.blendEquationCalls.get()
                + " colorMaskCalls=" + stub.colorMaskCalls.get()
                + " factorsCalled=" + stub.blendFactorsCalled + " equationCalled=" + stub.blendEquationCalled
                + " colorMaskCalled=" + stub.colorMaskCalled
                + " lastFactors=(" + stub.raster.blendSrcRgb() + "," + stub.raster.blendDstRgb() + ","
                + stub.raster.blendSrcAlpha() + "," + stub.raster.blendDstAlpha() + ")"
                + " lastEquations=(" + stub.raster.blendEquationRgb() + "," + stub.raster.blendEquationAlpha() + ")"
                + " lastColorMask=0x" + Integer.toHexString(stub.raster.colorMask());
    }

    /** {@return the game's clip rectangle}, or {@code null} when glScissor has not been called in this process. */
    public static int[] scissorBox() {
        CeltiumGlStub stub = installed;
        if (stub == null || !stub.raster.scissorCalled()) {
            return null;
        }
        return new int[] {stub.raster.scissorX(), stub.raster.scissorY(), stub.raster.scissorWidth(), stub.raster.scissorHeight()};
    }

    /** {@return how many times glScissor has been called in this process}, the scene-level clip census. */
    public static long scissorCallCount() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0L : stub.scissorCallCount;
    }

    /** {@return how many times GL_SCISSOR_TEST was enabled or disabled in this process}. */
    public static long scissorToggleCount() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0L : stub.scissorToggleCount;
    }

    /**
     * {@return whether GL_SCISSOR_TEST is enabled}. Read from the same {@code enables} map glEnable/glDisable write,
     * so there is one state rather than a mirror that can drift from it.
     */
    public static boolean scissorTest() {
        CeltiumGlStub stub = installed;
        return stub != null && stub.raster.scissorTest();
    }

    /**
     * {@return whether {@code glScissor} was called at all}.
     * <p>
     * The four readers below exist beside {@link #scissorBox()} so the backend can compare a group's captured
     * rectangle against the live one once per batch without allocating: {@code scissorBox()} builds a new array, and
     * the group's boundary test runs on every offered batch. They are the same four values; only the allocation
     * differs.
     */
    public static boolean scissorCalled() {
        CeltiumGlStub stub = installed;
        return stub != null && stub.raster.scissorCalled();
    }

    public static int scissorX() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : stub.raster.scissorX();
    }

    public static int scissorY() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : stub.raster.scissorY();
    }

    public static int scissorWidth() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : stub.raster.scissorWidth();
    }

    public static int scissorHeight() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : stub.raster.scissorHeight();
    }

    /** The stream the exit census is written to: the process's own stdout, not the logger Forge put in front of it. */
    private static volatile java.io.PrintStream exitStream;

    /**
     * Writes one census line where it will still exist when the process is gone.
     * <p>
     * {@code System.out} is routed into Log4j by Forge, and Log4j's shutdown hook stops the appenders; hooks run
     * concurrently, so a census printed from a hook is dropped whenever the logging hook gets there first. The file
     * descriptor the process started with is not part of that teardown. Falls back to {@code System.out} when the
     * descriptor cannot be wrapped - a census that might be lost is still better than none.
     */
    private static void celtium$printAtExit(String line) {
        String text = "[Celtium] " + line;
        try {
            java.io.PrintStream stream = exitStream;
            if (stream == null) {
                stream = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                        java.nio.charset.StandardCharsets.UTF_8);
                exitStream = stream;
            }
            stream.println(text);
        } catch (RuntimeException | LinkageError error) {
            System.out.println(text);
        }
    }

    /**
     * Records the backend's own main/non-main decision for the framebuffer that is now bound.
     * <p>
     * A clear's owner cannot be decided from the raw GL name: the game's main render target is not necessarily the
     * default framebuffer (name 0), so a rule that drops every clear with a non-zero name drops the frame's own
     * clears too - measured as 420 dropped clears with owner 1 and a picture gate that had been passing.
     */
    public static void noteDrawFramebufferIsMain(boolean isMain) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.drawFramebufferIsMain = isMain;
        }
    }

    /** {@return whether the pending clear belongs to the main render target}, false when no clear is pending. */
    public static boolean pendingClearIsMain() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return false;
        }
        return GlsmClearState.current().hasPending() && GlsmClearState.current().pendingIsMain();
    }

    /**
     * {@return the framebuffer the pending clear was issued against}, or -1 when no clear is pending. A non-zero
     * value means the clear names one of the game's own framebuffers rather than the delivered frame.
     */
    public static int pendingClearDrawFbo() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return -1;
        }
        return GlsmClearState.current().pendingDrawFbo();
    }

    /**
     * {@return whether a {@code glClear} is waiting to be applied to an image}.
     * <p>
     * The window flush asks this before it decides whether the frame image has to be moved into its attachment
     * layout: the clear is recorded in that layout, so a window that carries one must not skip the acquire. It is a
     * plain read of the same counter {@link #consumePendingClears()} drains, not a second bookkeeping of it.
     */
    public static boolean hasPendingClears() {
        CeltiumGlStub stub = installed;
        return stub != null && GlsmClearState.current().hasPending();
    }

    /** Uniform-block introspection: no block or active uniform exists, so the integer answer is zero. */
    public static void celtium$getActiveUniformInt(int program, int index, int pname, long out) {
        if (out != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(out, 0);
        }
    }

    /** {@code glGetInternalformativ/i64v}: {@code bufSize} unsupported-format zeros, in the caller's width. */
    public static void celtium$getInternalFormat(int bufSize, long params, boolean wide) {
        if (params == 0L || bufSize <= 0) {
            return;
        }
        int bounded = Math.min(bufSize, 1 << 12);
        for (int i = 0; i < bounded; i++) {
            if (wide) {
                org.lwjgl.system.MemoryUtil.memPutLong(params + (long) i * 8L, 0L);
            } else {
                org.lwjgl.system.MemoryUtil.memPutInt(params + (long) i * 4L, 0);
            }
        }
    }

    /** {@code glGetProgramResourceName}: an empty resource name, with the written length. */
    public static void celtium$getProgramResourceName(int program, int programInterface, int index, int bufSize,
            long length, long name) {
        if (length != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(length, 0);
        }
        if (name != 0L && bufSize > 0) {
            org.lwjgl.system.MemoryUtil.memPutByte(name, (byte) 0);
        }
    }

    /** {@code glGetProgramResourceiv}: no property values were produced, so the length says zero. */
    public static void celtium$getProgramResourceLength(int bufSize, long length) {
        if (length != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(length, 0);
        }
    }

    /** {@code glGetActiveUniformsiv}: {@code count} integers, all zero (no active uniform exists). */
    public static void celtium$getActiveUniforms(int count, long out) {
        if (out == 0L || count <= 0) {
            return;
        }
        int bounded = Math.min(count, 1 << 16);
        for (int i = 0; i < bounded; i++) {
            org.lwjgl.system.MemoryUtil.memPutInt(out + (long) i * 4L, 0);
        }
    }

    /** Uniform-block/active-uniform names: an empty name, with the written length that says so. */
    public static void celtium$getActiveUniformName(int program, int index, long length, long name, int bufSize) {
        if (length != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(length, 0);
        }
        if (name != 0L && bufSize > 0) {
            org.lwjgl.system.MemoryUtil.memPutByte(name, (byte) 0);
        }
    }

    /** {@code glGetProgramBinary}: no binary is produced, so the length and format say exactly that. */
    public static void celtium$getProgramBinary(int program, int bufSize, long length, long binaryFormat, long binary) {
        if (length != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(length, 0);
        }
        if (binaryFormat != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(binaryFormat, 0);
        }
    }

    /** {@code glGetShaderPrecisionFormat}: desktop float precision, which is what the pipelines carry. */
    public static void celtium$getShaderPrecisionFormat(int shaderType, int precisionType, long range, long precision) {
        if (range != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(range, 127);
            org.lwjgl.system.MemoryUtil.memPutInt(range + 4L, 127);
        }
        if (precision != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(precision, 23);
        }
    }

    /** {@code glGetShaderSource}: an empty source, written instead of leaving the caller's buffer untouched. */
    public static void celtium$getShaderSource(int shader, int bufSize, long length, long source) {
        if (length != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(length, 0);
        }
        if (source != 0L && bufSize > 0) {
            org.lwjgl.system.MemoryUtil.memPutByte(source, (byte) 0);
        }
    }

    /** {@code glGetAttachedShaders}: no shader is attached, which is also what the count pointer reports. */
    public static void celtium$getAttachedShaders(int program, int maxCount, long count, long shaders) {
        if (count != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(count, 0);
        }
    }

    /**
     * {@code glGetActiveUniform/glGetActiveAttrib} (and the ARB spellings): the stubbed shader surface has no active
     * objects, so the answer is an empty name with zero length/size/type - written, because an untouched caller buffer
     * is what the previous no-op left behind.
     */
    public static void celtium$getActiveObject(int program, int index, int bufSize, long length, long size, long type,
            long name) {
        if (length != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(length, 0);
        }
        if (size != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(size, 0);
        }
        if (type != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(type, 0);
        }
        if (name != 0L && bufSize > 0) {
            org.lwjgl.system.MemoryUtil.memPutByte(name, (byte) 0);
        }
    }

    /** A deterministic zero fill for an image readback this renderer cannot produce; bounded to a sane request. */
    public static void celtium$zeroBytes(long out, long bytes) {
        if (out == 0L || bytes <= 0L) {
            return;
        }
        org.lwjgl.system.MemoryUtil.memSet(out, 0, Math.min(bytes, 64L * 1024L * 1024L));
    }

    /** Deterministic zeros for an unmodelled getter's output, instead of leaving the caller's buffer uninitialised. */
    public static void celtium$zeroOut(long out, int count, boolean wide) {
        if (out == 0L || count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            if (wide) {
                org.lwjgl.system.MemoryUtil.memPutLong(out + (long) i * 8L, 0L);
            } else {
                org.lwjgl.system.MemoryUtil.memPutInt(out + (long) i * 4L, 0);
            }
        }
    }

    /**
     * {@code glGetQueryObject*} from the transformer's route. A result of zero would tell an occlusion-culling mod that
     * everything is hidden, so result and availability answer 1; a query this stub never ran still passes nothing
     * through the stencil, which is the visible outcome.
     */
    public static void celtium$getQueryObject(int query, int pname, long out, boolean wide) {
        if (out == 0L) {
            return;
        }
        long value = pname == 0x8866 || pname == 0x8867 ? 1L : 0L; // GL_QUERY_RESULT / GL_QUERY_RESULT_AVAILABLE
        if (wide) {
            org.lwjgl.system.MemoryUtil.memPutLong(out, value);
        } else {
            org.lwjgl.system.MemoryUtil.memPutInt(out, (int) value);
        }
    }

    /**
     * One recorded attribute answer, shared by the three widths of the {@code glGetVertexAttrib*} family. The
     * record is the bound VAO's own, per attribute: before the records existed these queries answered attribute 0
     * from the client-array fields and every other index with zero, so a caller asking after its own VAO setup
     * (the full-screen quad's renderer among them) was told its attributes did not exist.
     */
    private int celtium$vertexAttribAnswer(int index, int pname) {
        if (index < 0 || index >= MAX_VERTEX_ATTRIBS) {
            return 0;
        }
        GlsmClientArrayState.VaoAttrib attrib = boundAttribs()[index];
        switch (pname) {
            case 0x8622: // GL_VERTEX_ATTRIB_ARRAY_ENABLED
                // Attribute 0 keeps its two legacy spellings as well: the client-array enable and the capability
                // record the enable route writes are the same fact in older dress.
                return attrib.enabled || (index == 0 && (GlsmEnableState.current().enabled(0x8074) || GlsmEnableState.current().enabled(0x8622)))
                        ? 1 : 0;
            case 0x8623: // GL_VERTEX_ATTRIB_ARRAY_SIZE
                return attrib.size;
            case 0x8624: // GL_VERTEX_ATTRIB_ARRAY_STRIDE
                return attrib.stride;
            case 0x8625: // GL_VERTEX_ATTRIB_ARRAY_TYPE
                return attrib.type;
            case 0x889F: // GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING - the array buffer the pointer is an offset into
                return attrib.buffer;
            case 0x886A: // GL_VERTEX_ATTRIB_ARRAY_NORMALIZED
                return attrib.normalized ? 1 : 0;
            case 0x88FD: // GL_VERTEX_ATTRIB_ARRAY_INTEGER
                return attrib.integer ? 1 : 0;
            default:
                return 0;
        }
    }

    /** {@code glGetVertexAttribfv/dv}: the recorded attribute answer in the caller's numeric width. */
    public static void celtium$getVertexAttribFloats(int index, int pname, long out, boolean asDouble) {
        CeltiumGlStub stub = installed;
        if (stub == null || out == 0L) {
            return;
        }
        int value = stub.celtium$vertexAttribAnswer(index, pname);
        if (asDouble) {
            org.lwjgl.system.MemoryUtil.memPutDouble(out, value);
        } else {
            org.lwjgl.system.MemoryUtil.memPutFloat(out, value);
        }
    }

    /** {@code glGetVertexAttribPointerv}: the recorded pointer or buffer offset of the named attribute. */
    public static void celtium$getVertexAttribPointer(int index, int pname, long out) {
        CeltiumGlStub stub = installed;
        if (stub == null || out == 0L) {
            return;
        }
        long value = pname == 0x8645 && index >= 0 && index < MAX_VERTEX_ATTRIBS // GL_VERTEX_ATTRIB_ARRAY_POINTER
                ? stub.boundAttribs()[index].offset : 0L;
        org.lwjgl.system.MemoryUtil.memPutLong(out, value);
    }

    /** {@code glGetVertexAttribiv}, answered from the bound VAO's per-attribute record. */
    public static void celtium$getVertexAttribiv(int index, int pname, long out) {
        CeltiumGlStub stub = installed;
        if (stub == null || out == 0L) {
            return;
        }
        org.lwjgl.system.MemoryUtil.memPutInt(out, stub.celtium$vertexAttribAnswer(index, pname));
    }

    /** {@code glGetBooleani_v}: one GLboolean byte, the width GL writes for this query. */
    public static void celtium$getBooleanIndexed(long out) {
        if (out != 0L) {
            org.lwjgl.system.MemoryUtil.memPutByte(out, (byte) 0);
        }
    }

    /** {@code glGetIntegeri_v} from the transformer's route: the scalar answer, independent of the index. */
    public static void celtium$getIntegerIndexed(int pname, long out) {
        if (out != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(out, celtium$getInteger(pname));
        }
    }

    /** {@code glGetInteger64v/i_v} from the transformer's route: the scalar mirror widened to 64 bits. */
    public static void celtium$getInteger64(int pname, long out) {
        if (out != 0L) {
            org.lwjgl.system.MemoryUtil.memPutLong(out, celtium$getInteger(pname));
        }
    }

    /**
     * {@code glGetSynciv} from the transformer's route. The sync object glClientWaitSync already answers as signalled;
     * a zero here is not a sync status at all, and a caller polling GL_SYNC_STATUS would read it forever.
     */
    public static void celtium$getSynciv(long sync, int pname, int bufSize, long length, long values) {
        if (length != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(length, 1);
        }
        if (values != 0L) {
            org.lwjgl.system.MemoryUtil.memPutInt(values, pname == 0x9114 ? 0x9119 : 0); // GL_SYNC_STATUS -> GL_SIGNALED
        }
    }

    /**
     * {@code glGetFramebufferAttachmentParameteriv} (and its named form) from the transformer's route: the stub's
     * query path already answers the recorded colour attachment, and only the table's copy of it was unreachable.
     */
    public static void celtium$framebufferAttachmentParameter(boolean named, int framebufferOrTarget, int attachment,
            int pname, long out) {
        CeltiumGlStub stub = installed;
        if (stub == null || out == 0L) {
            return;
        }
        stub.status(new Fn(named ? "glGetNamedFramebufferAttachmentParameteriv"
                        : "glGetFramebufferAttachmentParameteriv", R_VOID,
                        new byte[] {A_INT, A_INT, A_INT, A_PTR}, SEM_STATUS),
                new long[] {framebufferOrTarget, attachment, pname, out});
    }

    /** {@code glClearBufferfv} from the transformer's route: colour or depth, with the caller's value. */
    public static void celtium$clearBufferfv(int buffer, int drawBuffer, long value) {
        CeltiumGlStub stub = installed;
        if (stub == null || value == 0L) {
            return;
        }
        if (buffer == 0x1800) { // GL_COLOR
            float red = org.lwjgl.system.MemoryUtil.memGetFloat(value);
            float green = org.lwjgl.system.MemoryUtil.memGetFloat(value + 4L);
            float blue = org.lwjgl.system.MemoryUtil.memGetFloat(value + 8L);
            float alpha = org.lwjgl.system.MemoryUtil.memGetFloat(value + 12L);
            GlsmClearState clear = GlsmClearState.current();
            float oldRed = clear.red(), oldGreen = clear.green(), oldBlue = clear.blue(), oldAlpha = clear.alpha();
            try {
                clear.clearColor(red, green, blue, alpha);
                stub.recordClear(0x4000);
            } finally {
                clear.clearColor(oldRed, oldGreen, oldBlue, oldAlpha);
            }
        } else if (buffer == 0x1801) { // GL_DEPTH
            double depth = org.lwjgl.system.MemoryUtil.memGetFloat(value);
            GlsmClearState clear = GlsmClearState.current();
            double old = clear.depth();
            try {
                clear.clearDepth(depth);
                stub.recordClear(0x0100);
            } finally {
                clear.clearDepth(old);
            }
        }
    }

    /** {@code glClearBufferfi} from the transformer's route: the depth/stencil pair clear. */
    public static void celtium$clearBufferfi(int buffer, int drawBuffer, float depth, int stencil) {
        CeltiumGlStub stub = installed;
        if (stub == null || buffer != 0x84F9) { // GL_DEPTH_STENCIL
            return;
        }
        GlsmClearState clear = GlsmClearState.current();
        double old = clear.depth();
        try {
            clear.clearDepth(depth);
            stub.recordClear(0x4100);
        } finally {
            clear.clearDepth(old);
        }
    }

    /**
     * {@code glGetShaderiv/glGetProgramiv/glGetObjectParameterivARB} from the transformer's route. A zero
     * compile/link status is a "failed" answer every caller checks, so the status pnames answer true by
     * default - but a program whose link {@link ModProgramRegistry} recorded answers with that link's result
     * instead, and its info-log length is the recorded failure's. Everything else (uniform counts...)
     * answers zero. See {@link #shaderProgramQueryAnswer(int, int)}.
     */
    public static void celtium$shaderObjectParameter(int object, int pname, long out) {
        if (out == 0L) {
            return;
        }
        org.lwjgl.system.MemoryUtil.memPutInt(out, shaderProgramQueryAnswer(object, pname));
    }

    /**
     * The answer for a shader/program status query, shared by the transformer's route and the native
     * dispatch. LINK_STATUS and VALIDATE_STATUS answer the recorded link's truth for a program the registry
     * knows ({@link ModProgramRegistry#linkStatus(int)}): a caller that checks - Iris's program builder,
     * Embeddium's {@code GlProgram} - then fails at load, with the reason in the info log, instead of
     * proceeding over a program whose passes this renderer refuses by name at prepare and which therefore
     * never draws. An id with no recorded link keeps the default true: its link may never have reached the
     * registry, and an absence this class knows nothing about must not condemn it. COMPILE_STATUS keeps the
     * default true for a different reason: compilation here happens at link, per program, so no per-shader
     * result exists when a caller asks right after {@code glCompileShader} - answering false there would
     * condemn shaders a later link compiles fine.
     */
    static int shaderProgramQueryAnswer(int object, int pname) {
        if (pname == 0x8B82 || pname == 0x8B83) { // GL_LINK_STATUS / GL_VALIDATE_STATUS
            Boolean recorded = ModProgramRegistry.linkStatus(object);
            return recorded == null || recorded ? 1 : 0;
        }
        if (pname == 0x8B84) { // GL_INFO_LOG_LENGTH
            String failure = ModProgramRegistry.linkInfoLog(object);
            return failure == null ? 0 : failure.length() + 1;
        }
        return pname == 0x8B81 ? 1 : 0; // GL_COMPILE_STATUS
    }

    /**
     * The cross-module entry to {@link #shaderProgramQueryAnswer(int, int)}: the GL-less hint answers
     * the LWJGL3 native queries through it, so the recorded link's truth is stated in exactly one
     * place and the transformer's route cannot drift from the dispatch's.
     */
    public static int celtium$shaderProgramQueryAnswer(int object, int pname) {
        return shaderProgramQueryAnswer(object, pname);
    }

    /**
     * Writes the recorded link failure of a program as its info log, bounded by the caller's buffer size,
     * and {@return the characters written}, excluding the NUL. With no recorded failure the log is the empty
     * string, as before. The failure text is the registry's own reason string, which is ASCII; a non-ASCII
     * character would be truncated by the byte cast, as in any C log.
     */
    public static int celtium$writeInfoLog(int object, int maxLength, long log) {
        String failure = ModProgramRegistry.linkInfoLog(object);
        if (failure == null || maxLength <= 0) {
            org.lwjgl.system.MemoryUtil.memPutByte(log, (byte) 0);
            return 0;
        }
        int count = Math.min(failure.length(), maxLength - 1);
        for (int i = 0; i < count; i++) {
            org.lwjgl.system.MemoryUtil.memPutByte(log + i, (byte) failure.charAt(i));
        }
        org.lwjgl.system.MemoryUtil.memPutByte(log + count, (byte) 0);
        return count;
    }

    /** Records a clear through the same producer used by callback dispatch. */
    public static void celtium$clear(int mask) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.recordClear(mask);
        }
    }

    public static void celtium$clearColor(float red, float green, float blue, float alpha) {
        GlsmHintLedger.current().noteArrival("glClearColor");
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.recordClearColor(red, green, blue, alpha);
        }
    }

    public static void celtium$clearDepth(double depth) {
        GlsmHintLedger.current().noteArrival("glClearDepth");
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.recordClearDepth(depth);
        }
    }

    /** {@return the game's current depth-write state}; assumed true when no backend is installed. */
    private static boolean depthWritesEnabled() {
        try {
            Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            return !(backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan)
                    || vulkan.celtium$depthWritesLive();
        } catch (RuntimeException | LinkageError error) {
            return true;
        }
    }

    private void recordClear(int mask) {
        mask = celtium$effectiveClearMask(mask);
        // The routing owner, not raw name 0, distinguishes the game's main framebuffer from other targets.
        // How often the game clears what it calls its main framebuffer, and with what colour, is the
        // GlsmClearState census (GAP-ST-4): issue() latches the pending mask and snapshots per buffer.
        if (this.drawFramebufferIsMain && CLEAR_CALLERS && this.mainClearCallerLogs < CLEAR_CALLER_LINES) {
            this.mainClearCallerLogs++;
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            StringBuilder callers = new StringBuilder(200);
            for (int i = 2; i < Math.min(stack.length, 11); i++) {
                callers.append(" <- ").append(stack[i].getClassName()).append('#').append(stack[i].getMethodName());
            }
            GlsmClearState clear = GlsmClearState.current();
            System.out.println("[Celtium] CELTIUM_MAIN_CLEAR mask=0x" + Integer.toHexString(mask)
                    + " colour=(" + clear.red() + "," + clear.green() + "," + clear.blue() + ","
                    + clear.alpha() + ") callers=" + callers);
        }
        GlsmClearState.current().issue(mask, this.drawFbo, this.drawFramebufferIsMain);
        if (this.drawFramebufferIsMain) {
            // The main framebuffer's clear can be carried by the draw queue instead of by the latch: the backend
            // attached it to the last queued group of the frame image when the ordering flush was asked for
            // (VulkanTerrainGpuBackend.flushBeforeClear), and this consumes the pending mask so the latch and the
            // window acquire do not apply the same clear a second time. A mask the backend did not carry is left
            // pending, so the frame machinery still applies it at the next pass begin.
            try {
                Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
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
                Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                    vulkan.celtium$gameClear(mask, this.drawFbo);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // The pending mask stays set, so the next bind's latch still carries the clear as before.
            }
        }
    }

    /**
     * The mask a {@code glClear} actually erases, after the two write-mask rules GL applies to it.
     * <p>
     * GL's clear obeys the colour write mask: a pass that clears depth with every colour channel masked off - the
     * fancy-cloud depth-only pass does exactly that through {@code GlStateManager.colorMask} - must not erase the
     * image's colour. The latch, the pending mask and every replay read this value, so the masking belongs at this one
     * choke point. A partial mask cannot be expressed by an attachment clear and still clears the colour.
     * <p>
     * The depth half is the same rule: {@code glClear} honours {@code glDepthMask}, and the game's depth writes are
     * mirrored on the backend (a {@code GlStateManager.depthMask} hook), not here, so the answer comes from there at the
     * moment the clear is issued. A pass that disabled depth writes and then cleared must not have the frame image's
     * depth erased.
     * <p>
     * Extracted because two callers have to agree on it: {@link #recordClear} applies it, and the backend's
     * {@code flushBeforeClear} asks it before it decides whether the queue can carry the clear - a reservation made
     * with the raw mask would not match the mask the clear is recorded with, and the clear would then be applied
     * twice.
     */
    public static int celtium$effectiveClearMask(int mask) {
        if (installed != null && installed.raster.colorMask() == 0) {
            mask &= ~0x4000;
        }
        if (!depthWritesEnabled()) {
            mask &= ~0x0100;
        }
        return mask;
    }

    private void recordClearColor(float red, float green, float blue, float alpha) {
        GlsmClearState.current().clearColor(red, green, blue, alpha);
    }

    private void recordClearDepth(double depth) {
        GlsmClearState.current().clearDepth(Math.max(0.0D, Math.min(1.0D, depth)));
    }

    /**
     * {@return the buffers the game asked {@code glClear} to clear since this was last called}, consuming them.
     * <p>
     * With no GL framebuffer to clear, the request is carried to the owned frame image instead: an image whose depth
     * still holds undefined values rejects every depth-tested draw, which is a whole frame of nothing rather than a
     * wrong pixel.
     */
    public static int consumePendingClears() {
        CeltiumGlStub stub = installed;
        if (stub == null) return 0;
        int consumed = GlsmClearState.current().consumePending();
        if (consumed != 0) FlushBoundaryCensus.current().note(FlushBoundaryCensus.Reason.CLEAR_CONSUME);
        return consumed;
    }

    /**
     * Puts back a main-framebuffer clear that {@link #consumePendingClears()} drained because the draw
     * queue was carrying it, after the group that carried it was discarded undrawn.
     * <p>
     * The restore is the record {@link #recordClear} made, replayed: the pending mask, the owner the
     * latch resolves the image from, and the per-buffer value snapshots {@link #mainClearValues} reads -
     * colour only when the mask names colour, depth only when it names depth, the same per-buffer rule
     * the record follows, so a depth-only rescue cannot replace the colour an earlier colour clear was
     * issued with. The frame machinery then applies the clear at the next pass begin, the route it would
     * have taken had the queue never carried it.
     */
    public static void celtium$rependMainClear(int mask, int drawFbo, float[] rgba, double depth) {
        CeltiumGlStub stub = installed;
        if (stub == null || mask == 0) {
            return;
        }
        GlsmClearState.current().repend(mask, drawFbo, rgba, depth);
    }

    /**
     * The colour and depth of the last {@code glClear} of the main framebuffer, as set when that clear was issued -
     * into {@code out} as {r,g,b,a}; {@return the depth}, or NaN when no main clear has been seen (use the live values).
     * <p>
     * The main clear is carried to the frame later (the latch at the next bind, or the acquire), and reading the live
     * {@code glClearColor} then gave whatever was set in between: a {@code Framebuffer.framebufferClear} sets its own
     * colour (black) before clearing another target, so the world frame was cleared black instead of with the fog
     * colour vanilla's clear used - the E2E pack's black area below the horizon beyond the loaded chunks.
     */
    public static double mainClearValues(float[] out) {
        CeltiumGlStub stub = installed;
        if (stub == null || !GlsmClearState.current().mainValuesKnown()) {
            clearColor(out);
            return Double.NaN;
        }
        GlsmClearState.current().mainValues(out);
        return GlsmClearState.current().lastMainDepth();
    }

    /** The colour the game last asked {@code glClear} to use, into {@code out} as {r,g,b,a}. */
    public static void clearColor(float[] out) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            out[0] = 0.0f;
            out[1] = 0.0f;
            out[2] = 0.0f;
            out[3] = 1.0f;
            return;
        }
        GlsmClearState clear = GlsmClearState.current();
        out[0] = clear.red();
        out[1] = clear.green();
        out[2] = clear.blue();
        out[3] = clear.alpha();
    }

    /** The depth the game last asked {@code glClear} to use. */
    public static double clearDepth() {
        CeltiumGlStub stub = installed;
        return stub == null ? 1.0 : GlsmClearState.current().depth();
    }

    // ---- texture captures: the answers the mirrors read --------------------------------------------------
    //
    // Extracted (giant-split seam 7, QUEUE 688): the capture answers moved to GlTextureCaptures, which owns
    // them now. The doors below keep their names and exact signatures; each answers the documented default
    // when no stub is installed and otherwise delegates, so no caller changes.

    /** The RGBA8 pixels the game uploaded for a texture level; see {@link GlTextureCaptures}. */
    public static java.nio.ByteBuffer textureUploadRgba8(int textureName, int level, int[] outWidthHeight) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            if (outWidthHeight != null && outWidthHeight.length >= 2) {
                outWidthHeight[0] = 0;
                outWidthHeight[1] = 0;
            }
            return null;
        }
        return stub.textureCaptures.textureUploadRgba8(textureName, level, outWidthHeight);
    }

    /** The texel offset the level's captured record covers inside its level; see {@link GlTextureCaptures}. */
    public static boolean textureUploadOffset(int textureName, int level, int[] outXY) {
        CeltiumGlStub stub = installed;
        return stub != null && stub.textureCaptures.textureUploadOffset(textureName, level, outXY);
    }

    /**
     * A read-only view of one backlog patch: the level region it covers and the write serial it carries.
     * Pixels come back as a read-only duplicate positioned at 0, same rule as {@link #textureUploadRgba8}.
     */
    public static final class PendingPatch {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final long serial;
        public final java.nio.ByteBuffer pixels;

        PendingPatch(GlTextureCaptures.TexCapture capture) {
            this.x = capture.x;
            this.y = capture.y;
            this.width = capture.width;
            this.height = capture.height;
            this.serial = capture.serial;
            java.nio.ByteBuffer view = capture.data.duplicate();
            view.clear();
            this.pixels = view.asReadOnlyBuffer();
        }
    }

    /** Whether the level has backlog patches waiting; see {@link GlTextureCaptures}. */
    public static boolean textureHasPendingPatches(int textureName, int level) {
        CeltiumGlStub stub = installed;
        return stub != null && stub.textureCaptures.textureHasPendingPatches(textureName, level);
    }

    /** A snapshot of the level's backlog patches, ordered by write serial; see {@link GlTextureCaptures}. */
    public static java.util.List<PendingPatch> texturePendingPatches(int textureName, int level) {
        CeltiumGlStub stub = installed;
        return stub == null
                ? new java.util.ArrayList<PendingPatch>()
                : stub.textureCaptures.texturePendingPatches(textureName, level);
    }

    /** Drops every backlog patch at or below {@code uptoSerial} for the level; see {@link GlTextureCaptures}. */
    public static void texturePatchesConsumed(int textureName, int level, long uptoSerial) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.textureCaptures.texturePatchesConsumed(textureName, level, uptoSerial);
        }
    }

    /** The serial the most recent level write took, any texture; see {@link GlTextureCaptures}. */
    public static long textureWriteEpoch() {
        CeltiumGlStub stub = installed;
        return stub == null ? Long.MIN_VALUE : stub.textureCaptures.textureWriteEpoch();
    }

    /** The write serial the level's captured record carries; see {@link GlTextureCaptures}. */
    public static long textureUploadSerial(int textureName, int level) {
        CeltiumGlStub stub = installed;
        return stub == null ? Long.MIN_VALUE : stub.textureCaptures.textureUploadSerial(textureName, level);
    }

    /** Whether the texture was allocated with a NULL data pointer; see {@link GlTextureCaptures}. */
    public static boolean textureAllocatedWithoutData(int textureName) {
        CeltiumGlStub stub = installed;
        return stub != null && stub.textureCaptures.textureAllocatedWithoutData(stub, textureName);
    }

    /** Whether the level exists but holds no game pixels; see {@link GlTextureCaptures}. */
    public static boolean textureLevelAllocatedWithoutPixels(int textureName, int level) {
        CeltiumGlStub stub = installed;
        return stub != null && stub.textureCaptures.textureLevelAllocatedWithoutPixels(textureName, level);
    }

    /** Whether the level holds pixels the game itself wrote; see {@link GlTextureCaptures}. */
    public static boolean textureLevelHasGamePixels(int textureName, int level) {
        CeltiumGlStub stub = installed;
        return stub != null && stub.textureCaptures.textureLevelHasGamePixels(textureName, level);
    }

    /** Whether a mirror upload of this level has provably nothing to read; see {@link GlTextureCaptures}. */
    public static boolean celtium$levelHasNothingToRead(int textureName, int level) {
        CeltiumGlStub stub = installed;
        return stub == null || stub.textureCaptures.celtium$levelHasNothingToRead(textureName, level);
    }

    /**
     * {@return whether a full mirror supply for this GL name has to wait for the framebuffer that renders into it}.
     * <p>
     * The registry's lookups already defer a name that is a framebuffer's colour attachment and has no captured
     * content: the image the name stands for is published by whoever owns the framebuffer, and an empty mirror built
     * here would be superseded (and retired) a moment later. The lazy supply in the immediate and model paths was the
     * one caller that bypassed that rule - it asked for an image directly, then ran a full upload whose only possible
     * answer for a render target is the capture-miss "the level was allocated with no data and no upload ever wrote
     * pixels into it". The cost is not the refusal, it is the image: one device allocation and one sampler per
     * render-target name, then a retire, and a counted capture miss that says a supply failed when there was nothing
     * to supply.
     * <p>
     * The answer stays "supply now" when the stub does hold content: a game pixel in level 0 (a texture that was
     * uploaded before it became an attachment) or a pending patch backlog (the game is filling the level now). Those
     * are the two sources the full upload can actually read, which is why they are the exception rather than the rule.
     * The same question is asked by {@code fillFromCapture} when it refuses an all-zero surface.
     *
     * @param textureName the GL texture name the bound batch samples
     */
    public static boolean celtium$mirrorWaitsForFramebuffer(int textureName) {
        if (textureName <= 0) {
            return false;
        }
        return celtium$mirrorWaitsForFramebuffer(celtium$framebufferForColorTexture(textureName) != 0,
                textureHasPendingPatches(textureName, 0), textureLevelHasGamePixels(textureName, 0));
    }

    /** The rule itself, with the three facts it reads passed in; see {@link GlTextureCaptures}. */
    static boolean celtium$mirrorWaitsForFramebuffer(boolean colourAttachment, boolean pendingPatches,
            boolean gamePixels) {
        return GlTextureCaptures.celtium$mirrorWaitsForFramebuffer(colourAttachment, pendingPatches, gamePixels);
    }

    /** {@link #celtium$supplyDecision}: the level holds content a full upload can read. */
    public static final int SUPPLY_NOW = GlTextureCaptures.SUPPLY_NOW;
    /** {@link #celtium$supplyDecision}: a framebuffer's colour attachment with nothing captured; its owner publishes. */
    public static final int SUPPLY_WAIT_FRAMEBUFFER = GlTextureCaptures.SUPPLY_WAIT_FRAMEBUFFER;
    /** {@link #celtium$supplyDecision}: no captured record and no patch backlog, so the upload would throw. */
    public static final int SUPPLY_NOTHING_TO_READ = GlTextureCaptures.SUPPLY_NOTHING_TO_READ;

    /** Which of the three supply outcomes this level has; see {@link GlTextureCaptures}. */
    public static int celtium$supplyDecision(int textureName, int level) {
        CeltiumGlStub stub = installed;
        return stub == null
                ? SUPPLY_NOTHING_TO_READ
                : stub.textureCaptures.celtium$supplyDecision(textureName, level);
    }

    /** One line of capture diagnostics; see {@link GlTextureCaptures}. */
    public static String textureCaptureSummary() {
        CeltiumGlStub stub = installed;
        return stub == null
                ? "texture capture: levels=0 full=0 patches=0 undefined=0 unconverted=0 bytes=0"
                : stub.textureCaptures.textureCaptureSummary();
    }

    /** The RGBA8 bytes the filed captures currently hold; see {@link GlTextureCaptures}. */
    public static long textureCaptureBytes() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0L : stub.textureCaptures.textureCaptureBytes();
    }

    /** Drops the stub's CPU copy of every captured level that has settled; see {@link GlTextureCaptures}. */
    public static long[] releaseSettledTextureCaptures() {
        CeltiumGlStub stub = installed;
        return stub == null
                ? new long[] { 0L, 0L }
                : stub.textureCaptures.releaseSettledTextureCaptures(stub);
    }

    final org.soutalab.celtium.vulkan.NativeRasterState raster;
    final org.soutalab.celtium.vulkan.NativeTextureBindings textures;
    private final ReadableMemoryRanges.Provider readableRangeProvider;

    private CeltiumGlStub() {
        this(org.soutalab.celtium.vulkan.NativeRasterState.current(),
                org.soutalab.celtium.vulkan.NativeTextureBindings.current(),
                ReadableMemoryRanges.defaultProvider());
    }

    CeltiumGlStub(ReadableMemoryRanges.Provider readableRangeProvider) {
        this(org.soutalab.celtium.vulkan.NativeRasterState.current(),
                org.soutalab.celtium.vulkan.NativeTextureBindings.current(),
                readableRangeProvider);
    }

    private CeltiumGlStub(org.soutalab.celtium.vulkan.NativeRasterState raster) {
        this(raster, new org.soutalab.celtium.vulkan.NativeTextureBindings(raster),
                ReadableMemoryRanges.defaultProvider());
    }

    private CeltiumGlStub(org.soutalab.celtium.vulkan.NativeRasterState raster,
            org.soutalab.celtium.vulkan.NativeTextureBindings textures) {
        this(raster, textures, ReadableMemoryRanges.defaultProvider());
    }

    private CeltiumGlStub(org.soutalab.celtium.vulkan.NativeRasterState raster,
            org.soutalab.celtium.vulkan.NativeTextureBindings textures,
            ReadableMemoryRanges.Provider readableRangeProvider) {
        this.raster = raster;
        this.textures = textures;
        this.readableRangeProvider = readableRangeProvider != null
                ? readableRangeProvider : ReadableMemoryRanges.defaultProvider();
        // -1 is "no binding recorded": texture and buffer names are generated ids and never negative, while 0 is
        // the GL "unbound" name a caller can legitimately store.
        this.textureBindings.defaultReturnValue(-1);
        // GL's initial GL_DEPTH_WRITEMASK is GL_TRUE. The record existed but started at zero, so a query before any
        // glDepthMask answered "writes disabled" - the opposite of the context's initial state.
        GlsmEnableState.current().set(GlStateQueries.GL_DEPTH_WRITEMASK_CAP, true);
    }

    // ---- FunctionProvider ------------------------------------------------------------

    @Override
    public long getFunctionAddress(ByteBuffer nameBuffer) {
        String name = StandardCharsets.UTF_8.decode(nameBuffer.duplicate()).toString();
        // Trim the trailing NUL the C caller supplies.
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
        // Every name gets a trampoline - the names the table knows get their semantics, the rest get a generic
        // no-op. Returning 0 would tell LWJGL capability detection the whole version or extension block is absent,
        // leaving the corresponding function pointers empty.
        Integer idValue = IDS.get(name);
        int id = idValue == null ? -1 : idValue.intValue();
        if (id < 0) {
            // BH-35: a name outside the table gets a generic no-op trampoline, and until now nothing recorded which
            // name it was - so a mod calling a GL entry point this stub does not model was swallowed with no trace at
            // all. The census names them (most frequent first) and the strict mode prints each one as it arrives, so
            // "unsupported" is a recorded boundary rather than a silent success.
            celtium$noteUnknownSymbol(name);
        }
        if (TRACE_GL_CALLS
                && ("glGetError".equals(name) || "glGetIntegerv".equals(name) || "glGetString".equals(name))) {
            System.out.println("[GLSTUB] addressFor " + name + " id=" + id);
        }
        byte[] args = id >= 0 ? TABLE.get(id).args : EMPTY_ARGS;
        return this.glTrampolines.addressFor(name, id, args);
    }

    // ---- dispatch ---------------------------------------------------------------------

    private static final int TRACE_LIMIT = Integer.getInteger("celtium.glstub.traceLimit", 40).intValue();
    /**
     * The trace switch, read once: {@link Boolean#getBoolean} is a map lookup and this method's callers run it on
     * every GL call the game makes - measured at ~2.6% of the warm main-thread samples as part of the dispatch cost.
     */
    private static final boolean TRACE = Boolean.getBoolean("celtium.glstub.trace");

    private final java.util.concurrent.atomic.AtomicInteger dispatchCount = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Handler failures the dispatch boundary swallowed (audit-gl-deep G1). Both upcall entries end in native code
     * that cannot receive a Java exception - an escape is a fatal JVM error on the FFM path and an unwritten
     * result slot on the libffi path - so the boundary catches, counts by function name, and answers zero. The
     * count is the point: a handler that throws on every call is a broken function, and it should read as a
     * growing number with a name, not as silence.
     */
    private static final java.util.concurrent.atomic.AtomicLong DISPATCH_FAILURES =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> DISPATCH_FAILURES_BY_NAME =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();
    /** Full stack traces are printed for the first few failures only; the counters above carry the rest. */
    private static final java.util.concurrent.atomic.AtomicInteger DISPATCH_FAILURE_LOGS =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final int DISPATCH_FAILURE_LOG_LIMIT = 8;

    private static void celtium$noteDispatchFailure(String name, Throwable failure) {
        long total = DISPATCH_FAILURES.incrementAndGet();
        java.util.concurrent.atomic.AtomicLong counter = DISPATCH_FAILURES_BY_NAME.get(name);
        if (counter == null) {
            java.util.concurrent.atomic.AtomicLong fresh = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicLong raced = DISPATCH_FAILURES_BY_NAME.putIfAbsent(name, fresh);
            counter = raced == null ? fresh : raced;
        }
        long forName = counter.incrementAndGet();
        if (DISPATCH_FAILURE_LOGS.incrementAndGet() <= DISPATCH_FAILURE_LOG_LIMIT) {
            System.out.println("[Celtium] CELTIUM_GL_DISPATCH_FAILURE fn=" + name + " count=" + forName
                    + " total=" + total + " error=" + failure.getClass().getName() + ": " + failure.getMessage()
                    + " (the call is answered with zero; a Java exception must not cross the upcall boundary)");
            failure.printStackTrace(System.err);
        }
    }

    /** {@return the table name for an id, or a bare id label when the id does not resolve}; for failure lines. */
    private static String celtium$fnNameForId(int functionId) {
        if (functionId >= 0 && functionId < TABLE.size()) {
            return TABLE.get(functionId).name;
        }
        return "id=" + functionId;
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

    /**
     * Dispatches refused because another thread was mid-dispatch (audit-gl-deep G2, remainder). The state tables
     * are open-addressed and confined to the dispatching thread; a foreign call that overlaps one is answered
     * with zero and counted here, by function name, instead of racing the tables. Sequential foreign arrivals
     * are served by the {@link DispatchThreadGate} and are not counted - this counter reads exactly the overlap
     * the confinement exists to prevent, so a growing number names both the function and the two threads.
     */
    private static final java.util.concurrent.atomic.AtomicLong FOREIGN_DISPATCH_REFUSALS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong>
            FOREIGN_DISPATCH_REFUSALS_BY_NAME =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();
    /** Full refusal lines are printed for the first few refusals only; the counters above carry the rest. */
    private static final java.util.concurrent.atomic.AtomicInteger FOREIGN_DISPATCH_REFUSAL_LOGS =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final int FOREIGN_DISPATCH_REFUSAL_LOG_LIMIT = 8;

    private static void celtium$noteForeignDispatch(String name, Thread arriving, Thread active) {
        long total = FOREIGN_DISPATCH_REFUSALS.incrementAndGet();
        java.util.concurrent.atomic.AtomicLong counter = FOREIGN_DISPATCH_REFUSALS_BY_NAME.get(name);
        if (counter == null) {
            java.util.concurrent.atomic.AtomicLong fresh = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicLong raced =
                    FOREIGN_DISPATCH_REFUSALS_BY_NAME.putIfAbsent(name, fresh);
            counter = raced == null ? fresh : raced;
        }
        long forName = counter.incrementAndGet();
        if (FOREIGN_DISPATCH_REFUSAL_LOGS.incrementAndGet() <= FOREIGN_DISPATCH_REFUSAL_LOG_LIMIT) {
            System.out.println("[Celtium] CELTIUM_GL_DISPATCH_FOREIGN fn=" + name + " count=" + forName
                    + " total=" + total + " arrivingThread=" + arriving.getName()
                    + " activeThread=" + (active == null ? "?" : active.getName())
                    + " (the call is answered with zero rather than raced against the GL state tables,"
                    + " which are confined to the thread inside dispatch)");
        }
    }

    /** {@return the foreign-dispatch refusals so far: the total and the worst offenders by name}. */
    public static String foreignDispatchSummary() {
        long total = FOREIGN_DISPATCH_REFUSALS.get();
        if (total == 0L) {
            return "foreignDispatchRefusals=0";
        }
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>> entries =
                new java.util.ArrayList<>(FOREIGN_DISPATCH_REFUSALS_BY_NAME.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder line = new StringBuilder("foreignDispatchRefusals=").append(total).append(" top=[");
        for (int i = 0; i < entries.size() && i < 8; i++) {
            if (i > 0) {
                line.append(' ');
            }
            line.append(entries.get(i).getKey()).append('=').append(entries.get(i).getValue().get());
        }
        return line.append(']').toString();
    }

    /**
     * The legacy (libffi) entry. {@code args} is an array of pointers, one per C argument, each pointing at that
     * argument's storage: the value is read out of the storage by declared type, then the call goes through the
     * same {@link #dispatch} as the FFM entry. The CIF return is always a pointer, so the result slot is always
     * written - narrower C callers read its low bits.
     */
    void legacyDispatch(int functionId, long result, long args) {
        long value;
        try {
            value = legacyDispatchBody(functionId, args);
        } catch (Throwable failure) {
            // The argument unpacking below reads caller memory directly, so a bad pointer throws here, before
            // dispatchArgs' own guard. The closure's result slot is still owed an answer: an exception escaping
            // into libffi leaves it unwritten and unwinds through native frames (audit-gl-deep G1).
            celtium$noteDispatchFailure(celtium$fnNameForId(functionId), failure);
            value = 0L;
        }
        APIUtil.apiClosureRetP(result, value);
    }

    private long legacyDispatchBody(int functionId, long args) {
        if (functionId < 0) {
            return 0L;
        }
        Fn fn = TABLE.get(functionId);
        long[] c = dispatchArgsScratch();
        for (int i = 0; i < fn.args.length && i < GlTrampolines.SLOTS; i++) {
            long slot = MemoryUtil.memGetAddress(args + (long) i * org.lwjgl.system.Pointer.POINTER_SIZE);
            switch (fn.args[i]) {
                case A_INT:
                case A_UINT:
                    c[i] = MemoryUtil.memGetInt(slot);
                    break;
                case A_PTR:
                    c[i] = MemoryUtil.memGetAddress(slot);
                    break;
                case A_LONG:
                    c[i] = MemoryUtil.memGetLong(slot);
                    break;
                case A_FLOAT:
                    c[i] = Float.floatToRawIntBits(MemoryUtil.memGetFloat(slot)) & 0xFFFFFFFFL;
                    break;
                case A_DOUBLE:
                    c[i] = Double.doubleToRawLongBits(MemoryUtil.memGetDouble(slot));
                    break;
                default:
                    break;
            }
        }
        return dispatchArgs(fn, c);
    }

    /**
     * Handles a call. {@code c0..c11} are the call's argument values in order - ints widened to longs, pointers as
     * raw addresses. Only the first {@code fn.args.length} slots are meaningful: the rest carry whatever the upcall
     * read past the arguments the C caller actually passed (or an explicit zero from the float/double entries). The
     * return value goes into the caller's result register; narrower results use the low bits.
     */
    private long dispatch(int functionId, long c0, long c1, long c2, long c3, long c4, long c5, long c6, long c7,
            long c8, long c9, long c10, long c11) {
        try {
            return dispatchBody(functionId, c0, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11);
        } catch (Throwable failure) {
            // dispatchArgs guards the handlers; this guard covers the table probe and the scratch packing above
            // them, so no Java exception crosses the FFM upcall boundary from this entry either (audit-gl-deep G1).
            celtium$noteDispatchFailure(celtium$fnNameForId(functionId), failure);
            return 0L;
        }
    }

    private long dispatchBody(int functionId, long c0, long c1, long c2, long c3, long c4, long c5, long c6, long c7,
            long c8, long c9, long c10, long c11) {
        // The no-op class owns the call volume - glColor/glTexCoord/glVertex and the state setters nobody reads -
        // and nothing in its path touches the arguments: the two censuses it pays were per-call String lookups
        // (a set contains + two concurrent-map gets, ~8% of the steady JFR) plus a twelve-slot scratch fill that
        // nobody reads. An index into the id-keyed arrays answers both questions and skips the whole pack.
        if (functionId >= 0 && TABLE.get(functionId).semantics == SEM_NOOP) {
            celtium$noteNoopById(functionId);
            return 0L;
        }
        long[] c = dispatchArgsScratch();
        c[0] = c0;
        c[1] = c1;
        c[2] = c2;
        c[3] = c3;
        c[4] = c4;
        c[5] = c5;
        c[6] = c6;
        c[7] = c7;
        c[8] = c8;
        c[9] = c9;
        c[10] = c10;
        c[11] = c11;
        return dispatchArgs(functionId, c);
    }

    /**
     * The per-call argument scratch: both entries below unpacked a call's arguments into a fresh
     * {@code new long[GlTrampolines.SLOTS]}, measured at tens of thousands of GL calls per frame - ~4 MB of garbage per frame
     * feeding every GC pause the p99 bands blame on the game. The first thread to call owns the shared array (a
     * field read and an identity compare, cheaper than {@code ThreadLocal.get} on a path this hot); a call on any
     * other thread falls back to a fresh array so a class-loading thread can never race the render thread's
     * arguments. Re-use is safe because dispatch handlers never call back through a C trampoline - a nested
     * {@link #legacyDispatch} would have to originate inside a handler, and handlers are pure Java - so one array
     * per owning thread cannot be corrupted by re-entry.
     */
    private final long[] dispatchScratch = new long[GlTrampolines.SLOTS];
    private final java.util.concurrent.atomic.AtomicReference<Thread> dispatchScratchOwner =
            new java.util.concurrent.atomic.AtomicReference<Thread>();

    /**
     * {@code -Dceltium.debug.fnCensus=true}: count arrivals per table entry and print the top producers at exit.
     * The per-call cost is one field increment on the Fn the dispatch already resolved - the question "which
     * functions make up the tens of thousands of trampoline calls per frame" has never had a counted answer.
     */
    private static final boolean FN_CENSUS = Boolean.getBoolean("celtium.debug.fnCensus");
    /** Per-pname arrival counts for {@code glGetIntegerv}, under {@link #FN_CENSUS}; see its call site. */
    private final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap getIntegervPnames =
            new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

    private long[] dispatchArgsScratch() {
        Thread calling = Thread.currentThread();
        if (calling == this.dispatchScratchOwner.get()) {
            return this.dispatchScratch;
        }
        // The claim is a CAS, not a check-then-set: two threads arriving together used to both read null and
        // both take the shared array, overwriting each other's arguments mid-dispatch, and the plain field gave
        // no visibility guarantee to the loser either (audit-gl-deep G2). The loser of the race - and every later
        // non-owner - keeps the fresh-array fallback, so the steady path is still one field read and an identity
        // compare.
        if (this.dispatchScratchOwner.compareAndSet(null, calling)) {
            return this.dispatchScratch;
        }
        if (calling == this.dispatchScratchOwner.get()) {
            return this.dispatchScratch;
        }
        return new long[GlTrampolines.SLOTS];
    }

    /**
     * Handles a call whose arguments are already unpacked. The legacy entry calls this directly with the array it
     * filled, so the call pays one allocation rather than the two it paid when {@link #dispatch} packed the same
     * arguments again.
     */
    private long dispatchArgs(int functionId, long[] c) {
        if (functionId < 0) {
            // A function outside the table: the generic no-op.
            return 0L;
        }
        return dispatchArgs(TABLE.get(functionId), c);
    }

    /**
     * Serializes dispatch occupancy across threads: the mechanism behind the state tables' thread policy
     * (audit-gl-deep G2, remainder). At most one thread is inside dispatch at a time. The occupying thread may
     * re-enter (list replay dispatches the recorded calls from inside a handler), tracked by a depth counter
     * only the occupying thread reads or writes - the handoff through the atomic reference's set/get gives the
     * next occupier the ordering it needs. A call that arrives while another thread occupies dispatch is
     * refused, never queued: the occupier is the render thread in every measured configuration, and making it
     * wait on a foreign thread would trade a named refusal for a stall (or a deadlock, if the foreign handler
     * ever waited back). Sequential arrivals from any thread are served - the gate is free again by the time
     * they arrive - which is what the measured startup arrivals (mod construction, {@code preInit},
     * class-loading threads) rely on.
     * <p>
     * The steady-state cost on the dispatching thread is one atomic read and an identity compare per call, the
     * same shape as the argument scratch's claim; the CAS is paid once per outermost dispatch, and only by a
     * call that finds the gate free or contended.
     */
    static final class DispatchThreadGate {
        /** The arriving thread now occupies dispatch (its depth went 0 to 1). */
        static final int ENTERED = 0;
        /** The arriving thread already occupied dispatch and re-entered (its depth grew). */
        static final int REENTERED = 1;
        /** Another thread occupies dispatch; the call must not proceed. */
        static final int REFUSED = -1;

        private final java.util.concurrent.atomic.AtomicReference<Thread> active =
                new java.util.concurrent.atomic.AtomicReference<Thread>();
        /** Nested-dispatch depth of the occupying thread; only that thread touches it. */
        private int depth;

        int enter(Thread self) {
            Thread current = this.active.get();
            if (current == self) {
                this.depth++;
                return REENTERED;
            }
            if (current == null && this.active.compareAndSet(null, self)) {
                this.depth = 1;
                return ENTERED;
            }
            return REFUSED;
        }

        void exit(Thread self) {
            if (--this.depth == 0) {
                this.active.set(null);
            }
        }

        /** {@return the thread inside dispatch, or {@code null} when the gate is free}; for refusal lines. */
        Thread activeThread() {
            return this.active.get();
        }
    }

    /** The one gate every dispatch on this stub passes through; see {@link DispatchThreadGate}. */
    private final DispatchThreadGate dispatchGate = new DispatchThreadGate();

    /**
     * The funnel every dispatch entry lands in. The thread gate runs first: a call that arrives while another
     * thread is mid-dispatch is refused and counted ({@link #celtium$noteForeignDispatch}) before it can touch
     * the state tables, and the gate is released on every exit path, including the guarded body's own failure
     * handling.
     */
    private long dispatchArgs(Fn fn, long[] c) {
        Thread self = Thread.currentThread();
        if (this.dispatchGate.enter(self) == DispatchThreadGate.REFUSED) {
            celtium$noteForeignDispatch(fn == null ? "<unresolved>" : fn.name, self,
                    this.dispatchGate.activeThread());
            return 0L;
        }
        try {
            return dispatchArgsGuarded(fn, c);
        } finally {
            this.dispatchGate.exit(self);
        }
    }

    /**
     * The guarded dispatch behind the thread gate. A handler that throws is answered with zero and counted by
     * name ({@link #celtium$noteDispatchFailure}), because both upcall entries end in native code that cannot
     * receive a Java exception (audit-gl-deep G1). While a display list is being compiled, the call's own
     * additions to the compilation are rolled back first: before this guard, an exception out of
     * {@code captureListPayload} or a handler left the compilation open with a half-recorded call in it, and
     * every later call kept appending to the broken compilation.
     * <p>
     * The snapshot is three field reads plus two list sizes, and only while a list is open - with no list open
     * there is nothing a failed call can leave half-recorded, and the wrapper costs the reads alone.
     */
    private long dispatchArgsGuarded(Fn fn, long[] c) {
        List<Object[]> openAtEntry = this.openList;
        int openSize = openAtEntry == null ? -1 : openAtEntry.size();
        int payloadsSize = openAtEntry == null || this.openListPayloads == null ? -1 : this.openListPayloads.size();
        int geometrySize = openAtEntry == null || this.openListGeometry == null ? -1 : this.openListGeometry.size();
        long legacyRunBytesAtEntry = this.openListLegacyRunBytes;
        try {
            return dispatchArgsBody(fn, c);
        } catch (Throwable failure) {
            celtium$rollbackFailedCompile(fn, openAtEntry, openSize, payloadsSize, geometrySize,
                    legacyRunBytesAtEntry);
            celtium$noteDispatchFailure(fn.name, failure);
            return 0L;
        }
    }

    /**
     * Undoes what one failed call appended to the list being compiled: recorded commands past the snapshot are
     * dropped, payload buffers they owned are freed and geometry buffers closed - the same releases the
     * list-abandonment path in SEM_NEW_LIST performs. The two delimiters own the compile state wholesale and
     * are not rolled back, and neither is a compilation that was closed or replaced during the call (the
     * identity check): rolling those back would resurrect state the delimiters already settled.
     */
    private void celtium$rollbackFailedCompile(Fn fn, List<Object[]> openAtEntry, int openSize, int payloadsSize,
            int geometrySize, long legacyRunBytesAtEntry) {
        if (openAtEntry == null || this.openList != openAtEntry) {
            return;
        }
        if (fn.semantics == SEM_NEW_LIST || fn.semantics == SEM_END_LIST) {
            return;
        }
        List<Object[]> open = this.openList;
        while (open.size() > openSize) {
            open.remove(open.size() - 1);
        }
        if (this.openListPayloads != null) {
            int keep = Math.max(payloadsSize, 0);
            for (int i = keep; i < this.openListPayloads.size(); i++) {
                org.lwjgl.system.MemoryUtil.memFree(this.openListPayloads.get(i));
            }
            while (this.openListPayloads.size() > keep) {
                this.openListPayloads.remove(this.openListPayloads.size() - 1);
            }
            if (payloadsSize < 0) {
                this.openListPayloads = null;
            }
        }
        if (this.openListGeometry != null) {
            int keep = Math.max(geometrySize, 0);
            for (int i = keep; i < this.openListGeometry.size(); i++) {
                this.openListGeometry.get(i).close();
            }
            while (this.openListGeometry.size() > keep) {
                this.openListGeometry.remove(this.openListGeometry.size() - 1);
            }
            if (geometrySize < 0) {
                this.openListGeometry = null;
            }
        }
        this.openListLegacyRunBytes = legacyRunBytesAtEntry;
    }

    /**
     * The dispatch with the table entry already looked up - the legacy entry needs the {@link Fn} for its arg-count
     * loop, so resolving it twice per call paid a second map probe on every GL call.
     */
    private long dispatchArgsBody(Fn fn, long[] c) {
        if (TRACE && this.dispatchCount.incrementAndGet() < TRACE_LIMIT) {
            System.out.println("[GLSTUB] #" + this.dispatchCount.get() + " " + fn.name
                    + " (" + Long.toHexString(c[0]) + "," + Long.toHexString(c[1]) + "," + Long.toHexString(c[2])
                    + "," + Long.toHexString(c[3]) + ")");
        }
        if (FN_CENSUS) {
            fn.calls++;
        }
        // A list being compiled does not draw. GL_COMPILE means "merely compiled" - the commands are stored and the
        // drawing happens when glCallList replays them - so while a list is open every call except the two that
        // delimit it is appended to that list instead of being executed. glCallList itself is appended as a call
        // rather than inlined, which is what the spec requires ("commands in the list or lists executed by glCallList
        // or glCallLists are not included in the display list being created") and what keeps a hierarchical list
        // correct when a child is replaced.
        List<Object[]> open = this.openList;
        if (open != null && fn.semantics != SEM_NEW_LIST && fn.semantics != SEM_END_LIST) {
            if (fn.semantics == SEM_DRAW_ARRAYS) {
                drawArrays((int) c[0], (int) c[1], (int) c[2], true);
                if (GlsmDisplayListLedger.current().compilingMode() != GL_COMPILE_AND_EXECUTE) return 0L;
            } else if (!notCompiledIntoDisplayList(fn.semantics)
                    && !fn.name.equals("glEnableClientState") && !fn.name.equals("glDisableClientState")) {
                long[] recorded = c.clone();
                java.nio.ByteBuffer owned = captureListPayload(fn, recorded);
                if (owned != null) {
                    if (this.openListPayloads == null) {
                        this.openListPayloads = new ArrayList<java.nio.ByteBuffer>();
                    }
                    this.openListPayloads.add(owned);
                }
                open.add(new Object[] { fn, recorded });
                if (GlsmDisplayListLedger.current().compilingMode() != GL_COMPILE_AND_EXECUTE) {
                    return 0L;
                }
                // GL_COMPILE_AND_EXECUTE: the command is stored *and* runs now. Falling through executes it; the stored
                // copy is what glCallList later replays. The old code returned here for both modes, so a list compiled
                // that way drew nothing until it was called - which the spec calls out as the difference between the two
                // modes (#393).
            }
            // Commands the spec does not compile run immediately and are not stored: a caller that asks for a value
            // during compilation has to get it, and a stored native out pointer would be written later through memory
            // that may no longer be a caller's (§5.4 of the OpenGL 1.2.1 specification).
        }
        long a0 = fn.args.length > 0 ? c[0] : 0L;
        long a1 = fn.args.length > 1 ? c[1] : 0L;
        switch (fn.semantics) {
            case SEM_GEN:
                return generate(fn, c);
            case SEM_FBO_COMPLETE:
                return 0x8CD5L; // GL_FRAMEBUFFER_COMPLETE
            case SEM_SIGNALED:
                return 0x911AL; // GL_ALREADY_SIGNALED
            case SEM_MAP:
                return mapBuffer(fn, c);
            case SEM_UNMAP:
                NativeGeometryBuffer mapped = NativeGeometryBuffer.find(bufferFor(fn, c));
                return mapped == null || mapped.unmap() ? 1L : 0L;
            case SEM_BUFFER_DATA:
                bufferData(fn, c);
                return 0L;
            case SEM_STATUS:
                status(fn, c);
                return 0L;
            case SEM_INFO_LOG:
                infoLog(fn, c);
                return 0L;
            case SEM_GET_ERROR:
                return 0L; // GL_NO_ERROR
            case SEM_GET_STRING:
                return stringFor((int) a0);
            case SEM_GET_STRINGI:
                return stringIndexed((int) a0, (int) a1);
            case SEM_IS_ENABLED:
                return enabledCap((int) a0) ? 1L : 0L;
            case SEM_ENABLE:
                setCapability((int) a0, true);
                return 0L;
            case SEM_DISABLE:
                setCapability((int) a0, false);
                return 0L;
            case SEM_CULL_FACE:
                this.raster.cullFace((int) a0);
                this.cullFaceCalled = true;
                return 0L;
            case SEM_FRONT_FACE:
                this.raster.frontFace((int) a0);
                this.frontFaceCalled = true;
                return 0L;
            case SEM_BLEND_FUNC:
                this.blendFuncCalls.incrementAndGet();
                this.raster.blendFunc((int) a0, (int) a1, (int) a0, (int) a1);
                this.blendFactorsCalled = true;
                return 0L;
            case SEM_BLEND_FUNC_SEPARATE:
                this.blendSeparateCalls.incrementAndGet();
                this.raster.blendFunc((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                this.blendFactorsCalled = true;
                return 0L;
            case SEM_BLEND_EQUATION:
                this.blendEquationCalls.incrementAndGet();
                this.raster.blendEquation((int) a0, (int) a0);
                this.blendEquationCalled = true;
                return 0L;
            case SEM_BLEND_EQUATION_SEPARATE:
                this.blendEquationCalls.incrementAndGet();
                this.raster.blendEquation((int) a0, (int) a1);
                this.blendEquationCalled = true;
                return 0L;
            case SEM_COLOR_MASK:
                this.colorMaskCalls.incrementAndGet();
                this.raster.colorMask(((int) c[0] != 0 ? 0x1 : 0) | ((int) c[1] != 0 ? 0x2 : 0)
                        | ((int) c[2] != 0 ? 0x4 : 0) | ((int) c[3] != 0 ? 0x8 : 0));
                this.colorMaskCalled = true;
                return 0L;
            case SEM_POLYGON_OFFSET:
                this.raster.polygonOffset(Float.intBitsToFloat((int) c[0]), Float.intBitsToFloat((int) c[1]));
                this.polygonOffsetCalls++;
                return 0L;
            case SEM_SCISSOR:
                this.raster.scissor((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                this.scissorCallCount++;
                return 0L;
            case SEM_GET_INTEGERV:
                if (FN_CENSUS) {
                    // pname, not the function: "glGetIntegerv" is the top arrival on every leg, and which parameter
                    // it asks for names the producer (GL_CURRENT_PROGRAM is this project's own batch preflight,
                    // GL_VIEWPORT the game's ActiveRenderInfo, a texture binding a mod's save/restore habit).
                    this.getIntegervPnames.put((int) a0, this.getIntegervPnames.get((int) a0) + 1L);
                }
                getInts((int) a0, a1, fn);
                return 0L;
            case SEM_GET_INTEGER64V:
                getLongs((int) a0, a1, fn);
                return 0L;
            case SEM_GET_FLOATV:
                getFloats((int) a0, a1, fn);
                return 0L;
            case SEM_GET_BOOLEANV:
                getBooleans((int) a0, a1, fn);
                return 0L;
            case SEM_IS_OBJECT:
                return GlsmNameLedger.current().isLive((int) a0) || GlsmNameLedger.current().isLiveSync(a0)
                        || "glIsBuffer".equals(fn.name) && NativeGeometryBuffer.find((int) a0) != null ? 1L : 0L;
            case SEM_DELETE:
                delete(fn, c);
                return 0L;
            case SEM_BIND_BUFFER:
                this.bindBuffer((int) a0, (int) a1);
                return 0L;
            case SEM_BIND_TEXTURE:
                if ((int) a0 == GL_TEXTURE_2D) this.textures.bindTexture2d((int) a1);
                else this.textureBindings.put(texBindKey((int) a0), (int) a1);
                noteBindSite((int) a1);
                return 0L;
            case SEM_BIND_IMAGE_TEXTURE:
                org.soutalab.celtium.vulkan.NativeImageBindings.current().bind((int) c[0], (int) c[1], (int) c[2],
                        c[3] != 0L, (int) c[4], (int) c[5], (int) c[6]);
                noteImageBind((int) c[0], (int) c[1]);
                return 0L;
            case SEM_ACTIVE_TEXTURE:
                // The unit index, not the raw enum: the getter adds the GL_TEXTURE0 base back, and a stored enum made
                // every save/restore round trip add another base (measured as the 0x84C0 ladder in the game's own
                // active-unit reads), which sent every unit-keyed binding record to a key no reader looked up.
                this.textures.activeUnit((int) a0 - 0x84C0);
                return 0L;
            case SEM_TEXIMAGE:
                texImage(c);
                return 0L;
            case SEM_TEXIMAGE_PIXELS:
                texImagePixels(fn, c);
                return 0L;
            case SEM_TEXSUBIMAGE:
                texSubImage(c);
                return 0L;
            case SEM_COMPRESSED_TEXIMAGE:
                compressedTexImage(c);
                return 0L;
            case SEM_PIXEL_STORE:
                pixelStore((int) c[0], (int) c[1]);
                return 0L;
            case SEM_VIEWPORT:
                GlsmViewportState.current().set((int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                return 0L;
            case SEM_FOG_I:
                org.soutalab.celtium.vulkan.ImmediateFogState.current().parameter((int) c[0], (int) c[1]);
                return 0L;
            case SEM_FOG_F:
                org.soutalab.celtium.vulkan.ImmediateFogState.current().parameter((int) c[0], Float.intBitsToFloat((int) c[1]));
                return 0L;
            case SEM_FOG_FV:
            case SEM_FOG_IV:
                if (c[1] != 0L) {
                    org.soutalab.celtium.vulkan.ImmediateFogState fog = org.soutalab.celtium.vulkan.ImmediateFogState.current();
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
                celtium$setTexEnvInt((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case SEM_TEX_ENV_F:
                // BH-34: an A_FLOAT slot carries the raw IEEE-754 bits, and glTexEnvf names the same enum as glTexEnvi -
                // 8448.0f arrives as 0x45040000, so casting the slot stored 1157955584 as the combine mode. Decode it
                // the way glTexParameterf next door already does, so the int and float forms record the same value.
                celtium$setTexEnvInt((int) c[0], (int) c[1], Math.round(Float.intBitsToFloat((int) c[2])));
                return 0L;
            case SEM_TEX_ENV_FV:
                celtium$setTexEnvFloats((int) c[0], (int) c[1], c[2]);
                return 0L;
            case SEM_GET_TEX_ENV_I:
                celtium$getTexEnvInt((int) c[0], (int) c[1], c[2]);
                return 0L;
            case SEM_GET_TEX_ENV_FV:
                celtium$getTexEnvFloats((int) c[0], (int) c[1], c[2]);
                return 0L;
            case SEM_COPY_TEX_IMAGE:
                if (isDepthInternalFormat((int) c[2])) {
                    // Same refusal as the transformer's route (celtium$copyTexImage2D): a depth copy is not read back.
                    recordExtent((int) c[0], (int) c[1], (int) c[2], (int) c[5], (int) c[6]);
                    this.depthCopiesSkipped++;
                    return 0L;
                }
                celtium$noteTextureCopy(fn.name, (int) c[0], (int) c[1], 0, 0, (int) c[3], (int) c[4],
                        (int) c[5], (int) c[6]);
                return 0L;
            case SEM_COPY_TEX_SUB_IMAGE:
                celtium$noteTextureCopy(fn.name, (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                        (int) c[5], (int) c[6], (int) c[7]);
                return 0L;
            case SEM_COPY_IMAGE_SUB_DATA:
                copyImageSubData((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], (int) c[5],
                        (int) c[6], (int) c[7], (int) c[8], (int) c[9], (int) c[10], (int) c[11],
                        (int) c[12], (int) c[13], (int) c[14]);
                return 0L;
            case SEM_BLIT_FRAMEBUFFER:
                celtium$noteBlitFramebuffer((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4],
                        (int) c[5], (int) c[6], (int) c[7], (int) c[8], (int) c[9]);
                return 0L;
            case SEM_READ_PIXELS:
                serveReadPixels((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], (int) c[5], c[6]);
                return 0L;
            case SEM_TEX_PARAM_I:
                celtium$setTextureParameterBound((int) c[0], (int) c[1],
                        "glTexParameterf".equals(fn.name) ? Math.round(Float.intBitsToFloat((int) c[2])) : (int) c[2],
                        celtium$paramSite());
                return 0L;
            case SEM_TEX_PARAM_IV:
                celtium$setTextureParameterBound((int) c[0], (int) c[1], celtium$firstParamValue(fn, c[2]),
                        celtium$paramSite());
                return 0L;
            case SEM_TEX_PARAM_I_DSA:
                celtium$setTextureParameter((int) c[0], (int) c[1],
                        "glTextureParameterf".equals(fn.name)
                                ? Math.round(Float.intBitsToFloat((int) c[2])) : (int) c[2],
                        celtium$paramSite());
                return 0L;
            case SEM_TEX_PARAM_IV_DSA:
                celtium$setTextureParameter((int) c[0], (int) c[1], celtium$firstParamValue(fn, c[2]),
                        celtium$paramSite());
                return 0L;
            case SEM_MULT_MATRIX:
                celtium$noteMatrixMultiplyArrival(fn);
                return 0L;
            case SEM_MATRIX_MODE:
                GlsmMatrixMode.current().set((int) c[0]);
                return 0L;
            case SEM_BIND_FRAMEBUFFER:
                bindFramebuffer((int) a0, (int) a1);
                return 0L;
            case SEM_CLEAR:
                this.recordClear((int) a0);
                return 0L;
            case SEM_CLEAR_COLOR:
                this.recordClearColor(Float.intBitsToFloat((int) c[0]), Float.intBitsToFloat((int) c[1]),
                        Float.intBitsToFloat((int) c[2]), Float.intBitsToFloat((int) c[3]));
                return 0L;
            case SEM_CLEAR_DEPTH:
                this.recordClearDepth("glClearDepth".equals(fn.name) ? Double.longBitsToDouble(c[0])
                        : (double) Float.intBitsToFloat((int) c[0]));
                return 0L;
            case SEM_BUFFER_SUBDATA:
                bufferSubData(fn, c);
                return 0L;
            case SEM_LINE_WIDTH:
                GlsmImmediateState.current().lineWidth(Float.intBitsToFloat((int) c[0]));
                return 0L;
            case SEM_USE_PROGRAM:
                GlsmShaderLedger.current().useProgram((int) c[0]);
                return 0L;
            case SEM_NEW_LIST:
                // A name of zero is not a list (the spec makes glNewList(0) GL_INVALID_VALUE), and recording under it
                // would file calls where no glCallList can reach them - which is the state this stub started in.
                // A glNewList that arrives while a compile is still open abandons that compile (GL has no nested
                // compiles, and the other layer's report of this same call is dropped inside the capture, so this
                // is the one place the abandonment shows). Close the abandoned session the way SEM_END_LIST closes
                // a finished one: its geometry buffers hold native memory, and the payloads and legacy run bytes
                // it collected would otherwise be filed under the new list's name at its glEndList.
                if (this.openList != null || this.openListGeometry != null || this.openListPayloads != null
                        || this.openListLegacyRunBytes > 0L) {
                    if (this.openListGeometry != null) {
                        for (NativeGeometryBuffer buffer : this.openListGeometry) {
                            buffer.close();
                        }
                    }
                    if (this.openListPayloads != null) {
                        for (int i = 0; i < this.openListPayloads.size(); i++) {
                            org.lwjgl.system.MemoryUtil.memFree(this.openListPayloads.get(i));
                        }
                    }
                    if (listAbandonedLogs < 8) {
                        listAbandonedLogs++;
                        System.out.println("[Celtium] CELTIUM_LIST_ABANDONED list=" + GlsmDisplayListLedger.current().compiling()
                                + " payloads=" + (this.openListPayloads == null ? 0 : this.openListPayloads.size())
                                + " legacyRunBytes=" + this.openListLegacyRunBytes);
                    }
                    this.openListGeometry = null;
                    this.openListPayloads = null;
                    this.openListLegacyRunBytes = 0L;
                    this.closeCompileState();
                }
                this.openListGeometry = null;
                GlsmDisplayListLedger.current().compiling((int) c[0]);
                GlsmDisplayListLedger.current().compilingMode((int) c[1]); // GL_LIST_MODE answers this, and the model capture asks for it
                this.openList = GlsmDisplayListLedger.current().compiling() == 0 ? null : new ArrayList<Object[]>();
                if (GlsmDisplayListLedger.current().compiling() > 0) {
                    this.openCompileState(GlsmDisplayListLedger.current().compilingMode());
                }
                // The model capture takes a new generation per compile. The GlStateManager path notifies it; a mod
                // that calls GL11.glNewList directly did not, so recompiling a name kept the mesh built from its
                // previous contents and the new geometry was never drawn.
                if (GlsmDisplayListLedger.current().compiling() > 0) {
                    org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listCompileStarted(GlsmDisplayListLedger.current().compiling());
                }
                return 0L;
            case SEM_END_LIST:
                // The spec replaces a name's contents only at glEndList, so the previous list stays callable until
                // this point; putting the new one in here is that rule, not an optimisation.
                List<Object[]> compiled = this.openList;
                if (compiled != null) {
                    // The spec replaces a name's contents at glEndList, so the payloads of the list this one displaces
                    // are unreachable from here on and are released now rather than held for the life of the process.
                    releaseListPayloads(Integer.valueOf(GlsmDisplayListLedger.current().compiling()));
                    GlsmDisplayListLedger.current().putList(GlsmDisplayListLedger.current().compiling(), compiled);
                    if (this.openListGeometry != null) GlsmDisplayListLedger.current().putGeometry(GlsmDisplayListLedger.current().compiling(), this.openListGeometry);
                    if (this.openListPayloads != null) {
                        GlsmDisplayListLedger.current().putPayloads(GlsmDisplayListLedger.current().compiling(), this.openListPayloads);
                    }
                    // The compiled legacy runs' bytes become this name's, and the release path above subtracts them
                    // from the same accounting when the name is replaced or deleted.
                    if (this.openListLegacyRunBytes > 0L) {
                        this.legacyListRunBytesByList.put(GlsmDisplayListLedger.current().compiling(), this.openListLegacyRunBytes);
                    }
                                        // What a compiled list holds, named once per list for the first few: a list that replays every
                    // frame and draws nothing is either not recorded at all (no line here) or recorded without a draw
                    // (draws=0), and the two need different fixes. The geometry bytes are the recorded
                    // COMPILED_ARRAYS payloads, which is what a replay can actually draw. Only lists carrying a
                    // non-trivial geometry print: the first compiles of a session are the model parts (a few hundred
                    // bytes each), while the lists whose absence changes the picture - a sky dome, a mod's skybox -
                    // are the large ones.
                    int draws = 0;
                    long bytes = 0L;
                    for (int i = 0; i < compiled.size(); i++) {
                        Object[] entry = compiled.get(i);
                        if (entry[0] == COMPILED_ARRAYS) {
                            long[] args = (long[]) entry[1];
                            draws++;
                            bytes += args[1] * args[3];
                        }
                    }
                    if (listCompileLogs < LIST_COMPILE_LOGS && bytes >= 4096L) {
                        listCompileLogs++;
                        System.out.println("[Celtium] CELTIUM_LIST_COMPILED list=" + GlsmDisplayListLedger.current().compiling()
                                + " commands=" + compiled.size() + " draws=" + draws + " bytes=" + bytes
                                + " legacyRunBytes=" + this.openListLegacyRunBytes
                                + " backend=" + (org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends
                                        .current() != null));
                    }
                } else if (this.openListPayloads != null) {
                    for (int i = 0; i < this.openListPayloads.size(); i++) {
                        org.lwjgl.system.MemoryUtil.memFree(this.openListPayloads.get(i));
                    }
                }
                if (compiled == null && this.openListGeometry != null)
                    for (NativeGeometryBuffer buffer : this.openListGeometry) buffer.close();
                this.openListGeometry = null;
                this.openListPayloads = null;
                this.openListLegacyRunBytes = 0L;
                this.openList = null;
                GlsmDisplayListLedger.current().compiling(0);
                this.closeCompileState();
                return 0L;
            case SEM_CALL_LIST:
                replayDisplayList((int) c[0]);
                return 0L;
            case SEM_LIST_BASE:
                GlsmDisplayListLedger.current().listBase((int) c[0]);
                return 0L;
            case SEM_CALL_LISTS:
                callLists((int) c[0], (int) c[1], c[2]);
                return 0L;
            case SEM_BIND_VERTEX_ARRAY:
                this.boundVertexArray = (int) c[0];
                // The same swap the transformer's route performs: the VAO's own attribute records become live
                // (the owner's arrays are keyed by the bound name, so naming the VAO is the whole swap).
                celtium$noteAttribZero();
                return 0L;
            case SEM_VERTEX_POINTER:
                // GL keeps one client vertex-array pointer; the last glVertexPointer wins, exactly as it does in a
                // driver. The pointer is a byte offset into the bound array buffer (GL_ARRAY_BUFFER) - the value the
                // game passes when a buffer is bound, which is the only case drawArrays() decodes.
                VERTEX_POINTER.size = (int) c[0];
                VERTEX_POINTER.type = (int) c[1];
                VERTEX_POINTER.stride = (int) c[2];
                VERTEX_POINTER.offset = c[3];
                GlsmClientArrayState.VaoAttrib pointerAttrib = boundAttribs()[0];
                pointerAttrib.size = (int) c[0];
                pointerAttrib.type = (int) c[1];
                pointerAttrib.stride = (int) c[2];
                pointerAttrib.offset = c[3];
                pointerAttrib.normalized = false;
                pointerAttrib.integer = false;
                pointerAttrib.pointerSet = true;
                pointerAttrib.buffer = NativeGeometryBuffer.boundId();
                // Bounded, and only while the layout census has room: the recorded tuple has to be attributable to a
                // call site, because a stride that does not match the sources' glVertexPointer(3, GL_FLOAT, 12, 0)
                // is either a call this class mis-reads or a caller nobody has looked at yet, and those two need
                // different fixes.
                if (org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(VERTEX_POINTER_SITES.size(), 12)
                        && VERTEX_POINTER_SITES.add(Integer.valueOf((int) c[0] * 31 + (int) c[1] * 7 + (int) c[2]))) {
                    System.out.println("[Celtium] CELTIUM_VERTEXPOINTER size=" + (int) c[0] + " type=0x"
                            + Integer.toHexString((int) c[1]) + " stride=" + (int) c[2] + " offset=" + c[3]
                            + " site=[" + celtium$callSite(8) + "]");
                }
                return 0L;
            case SEM_BEGIN:
                GlDrawCensus.celtium$noteImmediateBegin((int) c[0]);
                return 0L;
            case SEM_END:
                GlDrawCensus.BEGIN_END_CALLS.incrementAndGet();
                return 0L;
            case SEM_COMPILED_ARRAYS:
                replayCompiledArrays(c);
                return 0L;
            case SEM_DRAW_ARRAYS:
                drawArrays((int) c[0], (int) c[1], (int) c[2]);
                return 0L;
            case SEM_ROUTE:
                route(fn, c);
                return 0L;
            case SEM_NOOP:
            default:
                // GAP-FL-1 (F-11 specification): a guest glFlush/glFinish is counted in the flush
                // census as an arrival and never becomes a boundary.
                if ("glFlush".equals(fn.name) || "glFinish".equals(fn.name)) {
                    FlushBoundaryCensus.current().noteGuestFlush();
                }
                // A no-op is only safe for state the Vulkan side owns. For the transforms the state mirror is
                // supposed to follow, a no-op means the hook that should have mirrored the call never ran - which is
                // the silent fallback this class cannot afford, because without a context nothing else applies the
                // transform either. Counted per name so a run can say which one got through.
                celtium$noteMirrorMiss(fn.name);
                // And counted for every no-op, not only the transforms. This is the census that answers "which
                // vanilla drawing still goes nowhere under the stub" - a question that was answered by reading the
                // decompiled sources one call site at a time, and answered incompletely: RenderGlobal.renderSky
                // draws the sky dome, the stars and the horizon plane through VertexBuffer.drawArrays ->
                // GlStateManager.glDrawArrays, which is a no-op here, so all three were missing from every GL-less
                // picture while no counter mentioned them. A per-name count of every no-op finds that class of gap
                // by itself, including the ones nobody has thought to look for.
                celtium$noteNoop(fn.name);
                return 0L;
        }
    }

    /**
     * The transform calls worth counting.
     * <p>
     * This is an arrival census, not a hook-failure detector: the mirror's hooks run at the head of the
     * {@code GlStateManager} method and do not cancel the GL body, so a correctly mirrored call still arrives here.
     * What the count is for is the opposite comparison - it is the denominator. A name whose mirror is missing shows up
     * as arrivals that no {@code matrixMirrorDelta} application accounts for, which is how the missing float translate
     * was found (one translate per frame against dozens of tile entities). Read it with that counter, never alone.
     */
    private static final java.util.Set<String> MIRROR_REQUIRED = new java.util.HashSet<String>(java.util.Arrays.asList(
            "glTranslatef", "glTranslated", "glRotatef", "glRotated", "glScalef", "glScaled", "glOrtho",
            "glFrustum", "glLoadIdentity", "glLoadMatrixf", "glLoadMatrixd", "glMultMatrixf", "glMultMatrixd",
            "glPushMatrix", "glPopMatrix", "glMatrixMode"));

    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> MIRROR_MISSES =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();

    /**
     * The arrival/application pair for {@code glMultMatrixf/d} (task-50). Arrivals are counted here and applications
     * where the backend's mirror took the matrix, so a regression is the two numbers drifting apart - not a missing
     * line. {@code glMultMatrix*} used to be a no-op and was counted by {@link #MIRROR_MISSES}; it left that census
     * when it became a real handler, and this pair is what replaces it.
     */
    private static final java.util.concurrent.atomic.AtomicLong MATRIX_MULTIPLY_ARRIVALS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong MATRIX_MULTIPLY_APPLIED =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong MATRIX_MULTIPLY_NO_BACKEND =
            new java.util.concurrent.atomic.AtomicLong();
    /** A backend exists but does not implement the matrix mirror (the interface default is empty). */
    private static final java.util.concurrent.atomic.AtomicLong MATRIX_MULTIPLY_NO_MIRROR =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong MATRIX_MULTIPLY_REFUSED =
            new java.util.concurrent.atomic.AtomicLong();
    /** The first few multiplies, in one line each: which stack, which values, and who made the call. */

    /** Every no-op entry point that was actually called, by name. Bounded by the table's size. */
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> NOOP_CALLS =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();

    /** Entry points this stub does not model, by name; BH-35's boundary is this list, not a silent no-op. */
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> UNKNOWN_SYMBOLS =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();
    /** Lines of the strict mode's per-call record, bounded so a hot unknown symbol cannot flood a log. */
    private static final int STRICT_SYMBOL_LINES =
            Integer.getInteger("celtium.vulkan.strictSymbolLines", 200).intValue();
    private static final java.util.concurrent.atomic.AtomicInteger STRICT_SYMBOL_PRINTED =
            new java.util.concurrent.atomic.AtomicInteger();

    private static void celtium$noteUnknownSymbol(String name) {
        // The key printed on the F3 panel and in the log must itself be drawable text: a caller that hands
        // getFunctionAddress something other than a printable name - a truncated buffer, a raw pointer value, a
        // C string without its terminator - was recorded verbatim, and the census line then rendered those bytes as
        // the mojibake line the F3 screenshot shows (a mixture of CJK cells, box glyphs and quote fragments where the
        // GL symbol names should have been). GL entry points are printable ASCII; anything else is escaped per
        // character so the census still says what arrived without asking the font for a glyph that is not there.
        name = sanitizeSymbolName(name);
        java.util.concurrent.atomic.AtomicLong counter = UNKNOWN_SYMBOLS.get(name);
        if (counter == null) {
            counter = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicLong existing = UNKNOWN_SYMBOLS.putIfAbsent(name, counter);
            if (existing != null) {
                counter = existing;
            }
        }
        counter.incrementAndGet();
        if (STRICT_SYMBOLS
                && STRICT_SYMBOL_PRINTED.incrementAndGet() <= STRICT_SYMBOL_LINES) {
            // BH-35's acceptance asks which name was reached *and where from*: "a one-off probe by the game or a mod"
            // is a call-site claim, and a name without one cannot be told apart from a drawing path the renderer is
            // silently swallowing. Bounded with the names, so a hot unknown symbol cannot flood the log.
            System.out.println("[Celtium] CELTIUM_UNKNOWN_SYMBOL " + name
                    + " (no semantics in the table; the generic no-op trampoline answers it) at "
                    + celtium$callSite());
        }
    }

    /** {@return the first few frames above the caller}, so an unmodelled arrival carries its origin. */
    static String celtium$callSite() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        int shown = 0;
        for (int i = 3; i < stack.length && shown < 4; i++) {
            String className = stack[i].getClassName();
            // The stub's own trampoline frames say nothing about the caller; the first game/mod frame is what names
            // the probe.
            if (className.startsWith("org.soutalab.celtium.vulkan.gl.")) {
                continue;
            }
            if (shown++ > 0) {
                frames.append(" <- ");
            }
            frames.append(className).append('.').append(stack[i].getMethodName())
                    .append(':').append(stack[i].getLineNumber());
        }
        return frames.length() == 0 ? "(no frame above the stub)" : frames.toString();
    }

    /**
     * {@return the name with every byte outside printable ASCII escaped as {@code \xNN}, or {@code "<empty>"} when the
     * caller asked for the empty name}. GL entry points are short printable identifiers; a name that disagrees is a
     * caller that handed the lookup a buffer that was not a name, and the census has to show its arrival without
     * drawing undrawable glyphs. Names longer than 64 characters are truncated - an unterminated read keeps decoding
     * until the buffer runs out.
     */
    private static String sanitizeSymbolName(String name) {
        if (name == null || name.isEmpty()) {
            return "<empty>";
        }
        int limit = Math.min(name.length(), 64);
        StringBuilder out = new StringBuilder(limit + 8);
        for (int i = 0; i < limit; i++) {
            char c = name.charAt(i);
            if (c >= 0x20 && c <= 0x7E && c != ' ' && c != '=') {
                out.append(c);
            } else {
                out.append(String.format("\\x%02x", (int) c));
            }
        }
        if (name.length() > limit) {
            out.append("...(+").append(name.length() - limit).append(')');
        }
        return out.toString();
    }

    /** {@return the unmodelled entry points that were called, most frequent first}. */
    public static String unknownSymbolSummary() {
        if (UNKNOWN_SYMBOLS.isEmpty()) {
            return "none";
        }
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>> entries =
                new java.util.ArrayList<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>>(
                        UNKNOWN_SYMBOLS.entrySet());
        entries.sort((left, right) -> Long.compare(right.getValue().get(), left.getValue().get()));
        StringBuilder summary = new StringBuilder();
        int shown = 0;
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry : entries) {
            if (shown++ > 0) {
                summary.append(' ');
            }
            summary.append(entry.getKey()).append('=').append(entry.getValue().get());
            if (shown >= 24) {
                summary.append(" ...(").append(entries.size() - shown).append(" more)");
                break;
            }
        }
        return summary.toString();
    }

    /**
     * The per-call no-op accounting used to take a {@code String} all the way down: a {@code MIRROR_REQUIRED}
     * contains, a concurrent-map get and an atomic increment, twice - all on the highest-volume class of calls. The
     * fn id is the cheaper key the table already hands us: one array store per counter, and a bit-set lookup for
     * "is this a transform name". The name-keyed maps stay as the read model - the census readers iterate the
     * arrays and resolve names through the table, so nothing downstream changes shape.
     */
    private static final java.util.concurrent.atomic.AtomicLongArray NOOP_COUNTS_BY_ID =
            new java.util.concurrent.atomic.AtomicLongArray(4096);
    private static final java.util.concurrent.atomic.AtomicLongArray MIRROR_MISSES_BY_ID =
            new java.util.concurrent.atomic.AtomicLongArray(4096);
    private static final java.util.BitSet MIRROR_REQUIRED_BY_ID = new java.util.BitSet(4096);
    static {
        for (int i = 0; i < TABLE.size(); i++) {
            if (MIRROR_REQUIRED.contains(TABLE.get(i).name)) {
                MIRROR_REQUIRED_BY_ID.set(i);
            }
        }
    }

    private static void celtium$noteNoopById(int functionId) {
        if (MIRROR_REQUIRED_BY_ID.get(functionId)) {
            MIRROR_MISSES_BY_ID.incrementAndGet(functionId);
        }
        NOOP_COUNTS_BY_ID.incrementAndGet(functionId);
    }

    /**
     * The name-keyed no-op path remains for callers that genuinely have a name: it folds into the id arrays.
     * <p>
     * Public because the census's own test drives it: a census that can only be filled by a real GL call is a census
     * whose name list cannot be checked, and a name that is not in the table would leave it reading {@code none} for
     * ever. The production caller is the no-op trampoline.
     */
    public static void celtium$noteNoop(String name) {
        Integer id = IDS.get(name);
        if (id != null) {
            celtium$noteNoopById(id.intValue());
            return;
        }
        java.util.concurrent.atomic.AtomicLong counter = NOOP_CALLS.get(name);
        if (counter == null) {
            counter = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicLong existing = NOOP_CALLS.putIfAbsent(name, counter);
            if (existing != null) {
                counter = existing;
            }
        }
        counter.incrementAndGet();
    }

    /**
     * {@return the no-op entry points that were called, most frequent first}.
     * <p>
     * The question this answers is not "which calls did the stub swallow" - it swallows everything by design - but
     * "which of them would have drawn something". A name here that belongs to a drawing entry point (glDrawArrays,
     * glDrawElements, glCallList, the immediate-mode calls) is a vanilla path whose geometry reaches no image at all
     * under this provider, and that is a hole in the picture rather than a state detail. Read it before concluding
     * that a scene is complete.
     */
    public static java.util.List<String> noopCensus(int limit) {
        java.util.List<long[]> byId = new java.util.ArrayList<long[]>();
        for (int i = 0; i < TABLE.size(); i++) {
            long count = NOOP_COUNTS_BY_ID.get(i);
            if (count > 0) {
                byId.add(new long[] { i, count });
            }
        }
        byId.sort((left, right) -> Long.compare(right[1], left[1]));
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (int i = 0; i < byId.size() && i < limit; ++i) {
            out.add(TABLE.get((int) byId.get(i)[0]).name + "=" + byId.get(i)[1]);
        }
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry : NOOP_CALLS.entrySet()) {
            if (out.size() >= limit) {
                break;
            }
            out.add(entry.getKey() + "=" + entry.getValue().get());
        }
        return out;
    }

    /**
     * The no-op entry points whose whole job is to put geometry in the image.
     * <p>
     * The general census ranks every swallowed call by count, and the top of that list is always state - {@code glHint},
     * {@code glShadeModel}, the material and light setters - because those are called per batch. A drawing entry point
     * that reaches the no-op table is a different kind of fact: the transformer routes the indexed and instanced draws
     * to real handlers, so a count here means a call arrived by a route the transformer did not rewrite, and its
     * geometry reached no image at all. That is a hole in the picture, not a state detail, and it is invisible in a
     * count-ranked list that a thousand {@code glHint} calls sit on top of.
     * <p>
     * The names are the table's own: {@link #isNoopEntryPoint(String)} answers whether one of them is really registered
     * as a no-op, and {@code NoopDrawingCensusTest} pins that every name here is - a name that is not in the table can
     * never be counted, so a typo would leave a census that always reads "none" and looks like a clean run.
     */
    private static final String NOOP_DRAWING_NAMES_TEXT =
            "glDrawElements glDrawRangeElements glDrawElementsInstanced glDrawArraysInstanced"
                    + " glMultiDrawArrays glMultiDrawElements glDrawElementsBaseVertex"
                    + " glDrawArraysIndirect glMultiDrawArraysIndirect glMultiDrawElementsIndirect"
                    + " glDrawElementsIndirect glArrayElement glRects glBitmap glDrawPixels glCopyPixels";

    /**
     * Holds the drawing-name set, in its own class initializer.
     * <p>
     * Not a field of {@link CeltiumGlStub} on purpose: this class's own {@code <clinit>} is at HotSpot's
     * {@code HugeMethodLimit}. Measured while adding this census - the set built inline took the initializer to
     * <b>8030</b> bytes, over the 8000-byte limit, and {@code HugeMethodLimitTest} failed on it. Above the limit the
     * JIT never compiles the method, and this initializer is the one that registers every GL entry point, so it would
     * run interpreted for the whole session. A nested holder gets its own initializer and leaves the outer one alone.
     */
    private static final class NoopDrawingNames {
        static final java.util.Set<String> SET = java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<String>(java.util.Arrays.asList(NOOP_DRAWING_NAMES_TEXT.split(" "))));
    }

    /** {@return the drawing entry points {@link #noopDrawingCensusSummary()} reports on}. */
    public static java.util.Set<String> noopDrawingNames() {
        return NoopDrawingNames.SET;
    }

    /** {@return whether {@code name} is registered in the table as a no-op entry point}. */
    public static boolean isNoopEntryPoint(String name) {
        for (int i = 0; i < TABLE.size(); i++) {
            Fn fn = TABLE.get(i);
            if (fn.semantics == SEM_NOOP && fn.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@return whether {@code key} names a drawing entry point, with or without a descriptor}.
     * <p>
     * The two censuses spell a call differently and this is the join between them: the stub's table holds bare names
     * ({@code glDrawElements}), while the transformer's unrouted census holds the JVM key it rewrote
     * ({@code glDrawElements(IIIJ)V}). A bare-name {@code contains} against the second form is false for every entry,
     * so the drawing line would read {@code none} for ever - a census that cannot fire, which is the failure this
     * project keeps finding. The descriptor is therefore stripped here, once, and the test drives both spellings.
     */
    public static boolean isDrawingEntryPointKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        int paren = key.indexOf('(');
        String bare = paren > 0 ? key.substring(0, paren) : key;
        return NoopDrawingNames.SET.contains(bare);
    }

    /**
     * {@return one line naming every drawing entry point that was called and drew nothing, or {@code none}}.
     * <p>
     * Read it as the picture's missing-geometry list, not as a state census: a name here is geometry the run asked for
     * and no image received. {@code none} is the only answer that says the drawing surface is complete for the calls
     * that arrived; it says nothing about calls that never arrived.
     * <p>
     * <b>This is the trampoline's census, and in the shipped path it reads zero for a structural reason.</b> Measured
     * on leg {@code r40a-gate}: this line read {@code none} while {@code CELTIUM_GL_SURFACE} reported 47,078 unrouted
     * calls in the same run. The transformer rewrites the LWJGL natives and answers them itself, so a rewritten call
     * never reaches the trampoline that increments these counters - zero here means "the trampoline was not the path",
     * not "nothing was swallowed". The live drawing census is {@code CELTIUM_GL_SURFACE_DRAWING}, which is computed
     * from the transformer's own unrouted counts. This one still answers for a caller without the transformer (the CPU
     * contract check drives it directly), and it is kept because deleting it would leave that path uncounted.
     */
    public static String noopDrawingCensusSummary() {
        StringBuilder line = new StringBuilder();
        int named = 0;
        for (int i = 0; i < TABLE.size(); i++) {
            long count = NOOP_COUNTS_BY_ID.get(i);
            if (count <= 0L) {
                continue;
            }
            String name = TABLE.get(i).name;
            if (!NoopDrawingNames.SET.contains(name)) {
                continue;
            }
            if (named > 0) {
                line.append(' ');
            }
            line.append(name).append('=').append(count);
            named++;
        }
        return named == 0 ? "none" : line.toString();
    }

    /** {@return the number of distinct no-op entry points called, and the total number of calls}. */
    public static String noopCensusSummary() {
        long total = 0L;
        for (java.util.concurrent.atomic.AtomicLong counter : NOOP_CALLS.values()) {
            total += counter.get();
        }
        long entryPoints = NOOP_CALLS.size();
        for (int i = 0; i < TABLE.size(); i++) {
            long count = NOOP_COUNTS_BY_ID.get(i);
            if (count > 0) {
                total += count;
                entryPoints++;
            }
        }
        return "noopEntryPoints=" + entryPoints + " noopCalls=" + total
                + " top=[" + String.join(" ", noopCensus(15)) + "]";
    }

    // ---- the glDrawArrays / glDrawElements census lives in GlDrawCensus -------------------------------------
    //
    // Extracted (giant-split seam 5, QUEUE 653): the counters, refusal records, the immediate-begin
    // notes and both summary bodies moved to GlDrawCensus, which owns them now. The two public doors
    // below keep their names and exact output; drawArraySummary passes the client-array decode
    // counters this class still owns (they are written by the decode paths, not by the census).

    /** {@return the {@code glDrawArrays} census as one line: arrivals, drawn, empty, refused and why}. */
    public static String drawArraySummary() {
        return GlDrawCensus.drawArraySummary(clientPointerAccepted, ownedClientBufferDecoded,
                pointerOnlyDecoded, ownedClientBufferMismatch, readableMappingRanges, readableMappingFailure);
    }

    /** {@return the indexed census as one line: arrivals, drawn, refused and why}. */
    public static String drawElementSummary() {
        return GlDrawCensus.drawElementSummary();
    }

    /** Prints the glDrawArrays census line at powers of two, through the census that owns the counters. */
    private static void celtium$reportDrawArrays() {
        GlDrawCensus.celtium$reportDrawArrays(clientPointerAccepted, ownedClientBufferDecoded,
                pointerOnlyDecoded, ownedClientBufferMismatch, readableMappingRanges, readableMappingFailure);
    }


    private static void celtium$noteMirrorMiss(String name) {
        Integer id = IDS.get(name);
        if (id != null) {
            if (MIRROR_REQUIRED_BY_ID.get(id.intValue())) {
                MIRROR_MISSES_BY_ID.incrementAndGet(id.intValue());
            }
            return;
        }
        if (!MIRROR_REQUIRED.contains(name)) {
            return;
        }
        java.util.concurrent.atomic.AtomicLong counter = MIRROR_MISSES.get(name);
        if (counter == null) {
            counter = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicLong existing = MIRROR_MISSES.putIfAbsent(name, counter);
            if (existing != null) {
                counter = existing;
            }
        }
        counter.incrementAndGet();
    }

    /**
     * {@return a one-line census of the transform calls that reached the stub}. Arrivals are expected: the mirror
     * hooks do not cancel the GL body. Compare it against the mirror's own application counts - an arrival that the
     * mirror never applied is the gap.
     * <p>
     * {@code glMultMatrixf/d} is reported as an arrivals/applied pair rather than as a miss, because it now has a real
     * handler: arrivals without applications mean the backend or the device was missing, not that the call was
     * swallowed by the no-op list.
     */
    public static String stubTransformSummary() {
        StringBuilder line = new StringBuilder();
        long arrivals = MATRIX_MULTIPLY_ARRIVALS.get();
        if (arrivals > 0L || MATRIX_MULTIPLY_APPLIED.get() > 0L || MATRIX_MULTIPLY_REFUSED.get() > 0L
                || MATRIX_MULTIPLY_NO_BACKEND.get() > 0L || MATRIX_MULTIPLY_NO_MIRROR.get() > 0L) {
            line.append("glMultMatrix arrivals=").append(arrivals)
                    .append(" applied=").append(MATRIX_MULTIPLY_APPLIED.get())
                    .append(" noBackend=").append(MATRIX_MULTIPLY_NO_BACKEND.get())
                    .append(" noMirror=").append(MATRIX_MULTIPLY_NO_MIRROR.get())
                    .append(" refused=").append(MATRIX_MULTIPLY_REFUSED.get());
        }
        for (int i = 0; i < TABLE.size(); i++) {
            long misses = MIRROR_MISSES_BY_ID.get(i);
            if (misses > 0) {
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(TABLE.get(i).name).append('=').append(misses);
            }
        }
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry
                : new java.util.TreeMap<String, java.util.concurrent.atomic.AtomicLong>(MIRROR_MISSES).entrySet()) {
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(entry.getKey()).append('=').append(entry.getValue().get());
        }
        return line.length() == 0 ? "none" : line.toString();
    }

    /** GL_MODELVIEW / GL_PROJECTION / GL_TEXTURE, the stacks {@code glMultMatrix*} can multiply. */
    private static final int GL_MODELVIEW = 0x1700;
    private static final int GL_PROJECTION = 0x1701;
    private static final int GL_TEXTURE_MATRIX_MODE = 0x1702;
    /* The matrix mode itself moved to GlsmMatrixMode (GAP-ST-11): the stub route's write
       (SEM_MATRIX_MODE) and the mixin's skip read now meet in that one record. */

    /**
     * {@return whether the stub already believes {@code mode} is current}, for the GlStateManager mixin's redundant-
     * call skip.
     * <p>
     * The stub's own value is the authority, not a second cache in the mixin: code that calls
     * {@code GL11.glMatrixMode} directly - several vanilla render paths do - reaches the stub through the trampoline
     * and updates the field either way, so a skip keyed on this field can never leave the stub believing the wrong
     * mode. The value is a plain state word the GL already treats as idempotent, so skipping the identical call is
     * semantics-preserving by construction.
     */
    public static boolean celtium$matrixModeIsCurrent(int mode) {
        CeltiumGlStub stub = installed;
        return stub != null && GlsmMatrixMode.current().isCurrent(mode);
    }

    private static String matrixModeName(int mode) {
        switch (mode) {
            case GL_MODELVIEW: return "MODELVIEW";
            case GL_PROJECTION: return "PROJECTION";
            case GL_TEXTURE_MATRIX_MODE: return "TEXTURE";
            default: return "0x" + Integer.toHexString(mode);
        }
    }

    /**
     * Census only, and deliberately so: {@code glMultMatrixf} is the GL-API-wide call Forge's item transform uses
     * ({@code ForgeHooksClient.multiplyCurrentGlMatrix}) <b>and</b> the call {@code org.lwjglx.util.glu.Project}
     * reaches at the end of {@code gluPerspective}, whose frustum {@code GluProjectionMixin} has already mirrored
     * into the projection stack. Applying the raw call here would reflect that same operation twice, so the owner of
     * each operation is the entry that knows what it means - the Forge entry for item camera transforms (see
     * {@code ForgeHooksClientMixin}) and {@code GluProjectionMixin} for the frustum - and this counter is what says a
     * raw call arrived without one. Do not turn this back into an application without removing the other owner.
     */
    private void celtium$noteMatrixMultiplyArrival(Fn fn) {
        MATRIX_MULTIPLY_ARRIVALS.incrementAndGet();
        if (MATRIX_MULTIPLY_ARRIVALS.get() <= 4L
                && org.soutalab.celtium.vulkan.VulkanDiagnostics.enabled()) {
            System.out.println("[Celtium] CELTIUM_MULT_MATRIX arrival-only fn=" + fn.name
                    + " mode=" + matrixModeName(GlsmMatrixMode.current().get())
                    + " applied=" + MATRIX_MULTIPLY_APPLIED.get()
                    + " site=[" + celtium$callSite(8) + "]");
        }
    }


    private long generate(Fn fn, long[] c) {
        if (fn.ret == R_INT && fn.args.length <= 1 && lastPointerArg(fn) < 0) {
            // Scalar-return form (glCreateShader, glCreateProgram, glGenLists): the id is the return value.
            // glGenLists is the one caller that asks for a *contiguous range*: it returns n such that n .. n+range-1
            // are all free, and Minecraft relies on it - RenderGlobal holds starGLCallList = generateDisplayLists(3)
            // and then uses starGLCallList + 1 and + 2 for glSkyList and glSkyList2. Advancing the counter by one for
            // a range of three would hand those two names to the next caller instead.
            int range = 1;
            if ("glGenLists".equals(fn.name) && fn.args.length == 1) {
                range = (int) c[0];
                if (range <= 0) {
                    return 0; // The spec: a range of zero (or less) generates nothing and returns 0.
                }
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
            memPutInt(out + (long) i * 4L, name);
        }
        return 0L;
    }

    private static int lastPointerArg(Fn fn) {
        for (int i = fn.args.length - 1; i >= 0; i--) {
            if (fn.args[i] == A_PTR) {
                return i;
            }
        }
        return -1;
    }

    /** The count before the out array: the last int arg before the out pointer, or 1 when there is none. */
    private static int countFromArgs(Fn fn, long[] c, int outIndex) {
        for (int i = outIndex - 1; i >= 0; i--) {
            if (fn.args[i] == A_INT || fn.args[i] == A_UINT) {
                return (int) c[i];
            }
        }
        return 1;
    }

    private void delete(Fn fn, long[] c) {
        boolean textures = "glDeleteTextures".equals(fn.name);
        if (fn.args.length == 2 && fn.args[0] == A_INT && fn.args[1] == A_PTR) {
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
                int name = memGetInt(names + (long) i * 4L);
                GlsmNameLedger.current().removeName(name);
                if (vertexArrays) {
                    GlsmClientArrayState.current().forgetVaoAttribs(name);
                    if (this.boundVertexArray == name) {
                        this.boundVertexArray = 0;
                        // The deleted VAO's records go with it; the default VAO's array is re-read on next use.
                    }
                }
                if (renderbuffers) {
                    GlsmClientArrayState.current().removeRenderbufferSize(name);
                    if (GlsmClientArrayState.current().boundRenderbuffer() == name) {
                        GlsmClientArrayState.current().bindRenderbuffer(0);
                    }
                }
                if (textures) {
                    deleteTexture(name);
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
            // that this stub compiled owns payload buffers that go with it (#394). The name is retired too, so glIsList
            // answers false afterwards - the audit measured glIsList(L)==1 and displayLists.size()==1 after the delete
            // (#395).
            deleteListsRange((int) c[0], (int) c[1]);
            return;
        }
        if (fn.args.length == 1) {
            if (fn.args[0] == A_LONG) {
                GlsmNameLedger.current().removeSync(c[0]);
            } else {
                int name = (int) c[0];
                GlsmNameLedger.current().removeName(name);
                if ("glDeleteBuffers".equals(fn.name)) {
                    forgetBuffer(name);
                }
                if ("glDeleteProgram".equals(fn.name)) {
                    // The registry's entry carries lowered SPIR-V and live uniform values; leaving it behind
                    // handed both to whatever object recycled the name (item 328, V-04). Until now only the
                    // forge122 transformer route reached the registry's delete.
                    ModProgramRegistry.deleteProgram(name);
                }
                if ("glDeleteShader".equals(fn.name)) {
                    ModProgramRegistry.deleteShader(name);
                }
            }
        }
    }

    /**
     * Retires a buffer's storage and its bindings.
     * <p>
     * Deleting a buffer left {@code bufferStore} holding its direct memory and {@code bufferBindings} still naming it, so
     * a later {@code glMapBuffer} answered with a pointer into the deleted buffer's storage and every delete-then-create
     * cycle kept another megabyte alive (#386).
     *
     * @param name the buffer name being deleted
     */
    private void forgetBuffer(int name) {
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
        if (nativeBuffer != null) nativeBuffer.close();
        if (NativeGeometryBuffer.boundId() == name) NativeGeometryBuffer.unbind();
        GlsmBufferState.current().removeStorage(name);
        this.bufferStorage.forget(name);
        if (this.pixelPackBuffer == name) {
            this.pixelPackBuffer = 0;
        }
        if (this.pixelUnpackBuffer == name) {
            this.pixelUnpackBuffer = 0;
        }
        GlsmBufferState.current().unbindName(name);
    }

    private void deleteTexture(int name) {
        org.soutalab.celtium.vulkan.NativeImageBindings.current().delete(name);
        if (name != 0) {
            Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                for (Integer fbo : GlsmFramebufferAttachments.current().framebuffersWithColorTexture(name)) {
                    vulkan.celtium$rawAttachmentTextureDeleted(fbo.intValue());
                }
                vulkan.celtium$rawTextureDeleted(name);
            }
        }
        int[] level0 = this.texLevels.get(texLevelKey(name, 0));
        if (level0 != null && level0[0] * level0[1] >= 1024 * 1024 && RESPECIFIED_LINES.getAndDecrement() > 0) {
            StringBuilder callers = new StringBuilder();
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 2; i < stack.length && i < 14; i++) {
                callers.append(i == 2 ? "" : " <- ").append(stack[i].getClassName().replaceAll(".*\\.", ""))
                        .append('.').append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
            }
            System.out.println("[Celtium] CELTIUM_TEXTURE_DELETED name=" + name + " " + level0[0] + "x" + level0[1]
                    + " callers=[" + callers + "]");
        }
        this.textures.delete(name);
        // The "allocated without data" classification belongs to the deleted object (item 328, V-04): the
        // TextureUtil route removed it in forgetTextureAllocation, the raw route left it standing, and a
        // recycled name was then read as render-target idiom it never declared.
        GlsmNameLedger.current().removeNullAllocated(name);
        if (!this.textureBindings.isEmpty()) {
            it.unimi.dsi.fastutil.longs.LongIterator bindings = this.textureBindings.keySet().iterator();
            while (bindings.hasNext()) {
                if (this.textureBindings.get(bindings.nextLong()) == name) bindings.remove();
            }
        }
        forgetTexture(name);
    }

    /** Invalidates storage/content for replacement. Unlike deletion, allocation must preserve the current binding. */
    private void forgetTexture(int name) {
        // The name's diagnostic and version records belong to the deleted object too (item 328, V-04): a
        // recycled name must not inherit its predecessor's first-bind site in refusal messages, nor its
        // parameter version - the version feeds the sampler re-confirm comparison in VulkanGlTexture, where a
        // stale count could stand in for a confirmation the new object never had.
        this.firstBindSites.remove(name);
        // The parameter record drops the name's version and recorded values itself, for the reasons its own
        // javadoc gives (a recycled name must not inherit the old wrap/filter, #389).
        this.textureParameters.forget(name);
        int maxLevel = this.maxRecordedTexLevel;
        if (maxLevel < 0) {
            return;
        }
        boolean hasLevels = !this.texLevels.isEmpty();
        boolean hasCaptures = !this.texCaptures.isEmpty();
        boolean hasBacklog = !this.texPatchBacklog.isEmpty();
        if (!hasLevels && !hasCaptures && !hasBacklog) {
            return;
        }
        for (int level = 0; level <= maxLevel; level++) {
            long key = texLevelKey(name, level);
            if (hasLevels) {
                this.texLevels.remove(key);
            }
            if (hasCaptures) {
                this.texCaptures.remove(key);
            }
            synchronized (this.suppliedSerials) {
                this.suppliedSerials.remove(key);
            }
            if (hasBacklog) {
                java.util.List<GlTextureCaptures.TexCapture> pending = this.texPatchBacklog.remove(key);
                if (pending != null) {
                    for (GlTextureCaptures.TexCapture capture : pending) {
                        this.textureCaptures.texPatchBytes -= capture.data.capacity();
                    }
                }
            }
        }
    }

    // ---- state queries ---------------------------------------------------------------
    //
    // Extracted (giant-split seam 6, QUEUE 674): the glGet* answer bodies moved to
    // GlStateQueries, which owns them now. The doors below keep their names and exact
    // answers; the implementations read this stub's recorded state through the stub
    // reference they are handed, so the state itself stays here.

    /** {@return the stub's answer to a GL string pname}, answered by {@link GlStateQueries}. */
    private long stringFor(int pname) {
        return GlStateQueries.stringFor(this, pname);
    }

    private long stringIndexed(int pname, int index) {
        return GlStateQueries.stringIndexed(this, pname, index);
    }

    /** The native image binding ledger size, under its established public name; the constant lives in {@link GlStateQueries}. */
    public static final int IMAGE_UNITS_PER_PROGRAM = GlStateQueries.IMAGE_UNITS_PER_PROGRAM;

    /** Reports the first answer this JVM gives for each texture-unit limit pname; see {@link GlStateQueries}. */
    public static void noteTextureUnitAnswer(int pname, int value) {
        GlStateQueries.noteTextureUnitAnswer(pname, value);
    }

    public static void noteExtensionAnswer(String surface, String extensions, int count) {
        GlStateQueries.noteExtensionAnswer(surface, extensions, count);
    }

    public static void noteImageUnitAnswer(int pname, int value) {
        GlStateQueries.noteImageUnitAnswer(pname, value);
    }

    private static void noteImageBind(int unit, int texture) {
        GlStateQueries.noteImageBind(unit, texture);
    }

    /** {@return the stub's answer to {@code glGetInteger(pname)} for the tracked scalar parameters}; see {@link GlStateQueries}. */
    public static int celtium$getInteger(int pname) {
        return GlStateQueries.celtium$getInteger(pname);
    }

    /** {@return the program the stub last bound}, or {@link Integer#MIN_VALUE} when no stub is installed. */
    public static int celtium$currentProgram() {
        return GlStateQueries.celtium$currentProgram();
    }

    /** Records the program {@code glUseProgram} bound; see {@link GlStateQueries}. */
    public static void celtium$useProgram(int program) {
        GlStateQueries.celtium$useProgram(program);
    }

    private void getBufferParameterQuery(String name, int target, int pname, long out) {
        GlStateQueries.getBufferParameterQuery(this, name, target, pname, out);
    }

    /** LWJGL3's buffer-parameter query natives; see {@link GlStateQueries}. */
    public static void celtium$getBufferParameter(String name, int target, int pname, long out) {
        GlStateQueries.celtium$getBufferParameter(name, target, pname, out);
    }

    private void getTexParameterQuery(String name, int target, int paramPname, long out) {
        GlStateQueries.getTexParameterQuery(this, name, target, paramPname, out);
    }

    /** LWJGL3's tex-parameter query natives, which bypass the dispatch path; see {@link GlStateQueries}. */
    public static void celtium$getTexParameter(String name, int target, int pname, long out) {
        GlStateQueries.celtium$getTexParameter(name, target, pname, out);
    }

    /** Answers tracked integer state for LWJGL3's replaced native query; see {@link GlStateQueries}. */
    public static boolean celtium$getIntegers(int pname, long out) {
        return GlStateQueries.celtium$getIntegers(pname, out);
    }

    private void getInts(int pname, long out, Fn fn) {
        GlStateQueries.getInts(this, pname, out, fn);
    }

    private void getLongs(int pname, long out, Fn fn) {
        GlStateQueries.getLongs(this, pname, out, fn);
    }

    private void getFloats(int pname, long out, Fn fn) {
        GlStateQueries.getFloats(this, pname, out, fn);
    }

    /** {@return whether the boolean query was answered}; the caller's buffer receives one byte per component. */
    public static boolean celtium$getBooleans(int pname, long out) {
        return GlStateQueries.celtium$getBooleans(pname, out);
    }

    /** {@return whether the capability is enabled}, answered from the same record {@code glIsEnabled} reads. */
    public static boolean celtium$isEnabled(int capability) {
        return GlStateQueries.celtium$isEnabled(capability);
    }

    /** The same state-query implementation for callback dispatch and LWJGL3's replaced native entry points. */
    public static void celtium$getFloats(int pname, long out, boolean asDouble) {
        GlStateQueries.celtium$getFloats(pname, out, asDouble);
    }

    private void getFloats(int pname, long out, boolean asDouble) {
        GlStateQueries.getFloats(this, pname, out, asDouble);
    }

    private void getBooleans(int pname, long out, Fn fn) {
        GlStateQueries.getBooleans(this, pname, out, fn);
    }

    /** {@return the live Vulkan backend, or null when another renderer (or none) is installed}. */
    private static org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkanBackend() {
        return GlStateQueries.vulkanBackend();
    }

    private boolean enabledCap(int cap) {
        return GlStateQueries.enabledCap(this, cap);
    }

    /** Pixel/semantic routing - the layer the texture path plugs into; the body lives in {@link GlStateQueries}. */
    private void route(Fn fn, long[] c) {
        GlStateQueries.route(fn, c);
    }
    // ---- texture image extents: what glGetTexLevelParameter answers from ---------------------------------

    /** GL_FRAMEBUFFER (0x8D40) binds both slots; GL_DRAW/READ_FRAMEBUFFER (0x8CA9/0x8CA8) bind one each. */
    private void bindFramebuffer(int target, int name) {
        if (target == 0x8D40 || target == 0x8CA9) {
            this.drawFbo = name;
        }
        if (target == 0x8D40 || target == 0x8CA8) {
            this.readFbo = name;
        }
        this.framebufferBindSerial++;
        FlushBoundaryCensus.current().note(FlushBoundaryCensus.Reason.FBO_BIND);
        // The registration the renderer makes from a raw bind (identity, records, pair, shared-depth borrow)
        // is triggered here rather than in the hint's report: every raw bind lands in this record, including
        // the ones GLSM's state cache and the vanilla-bracket suppression kept the hint's report from ever
        // seeing for the shadow framebuffers (legs 14/15: served native-depth copies = 0). The hint suppresses
        // the trigger while a vanilla Framebuffer method runs, because those binds are the object's own and the
        // mixin reports them by object identity.
        if (target != 0x8CA8 && bindReportSuppression == 0) {
            Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                vulkan.celtium$registerRawFramebuffer(name, true);
            }
        }
    }

    /**
     * Vanilla-bracket suppression of the bind registration above, driven by {@code CeltiumGllessHint} from the
     * same counter {@code FramebufferMixin} brackets: while a vanilla framebuffer method runs, its internal raw
     * binds belong to the object the mixin reports, not to a raw identity.
     */
    private static int bindReportSuppression;

    /** Suppresses the bind registration for the duration of one vanilla-bracketed bind. */
    public static void celtium$pushBindReportSuppression() {
        bindReportSuppression++;
    }

    /** Ends one {@link #celtium$pushBindReportSuppression}. */
    public static void celtium$popBindReportSuppression() {
        if (bindReportSuppression > 0) bindReportSuppression--;
    }

    /** Registers a raw framebuffer with the renderer without a bind (the hint's unbind-on-delete path). */
    public static void celtium$registerRawFramebuffer(int framebuffer) {
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            vulkan.celtium$registerRawFramebuffer(framebuffer, true);
        }
    }

    /** Bumped by every framebuffer bind, which is the only thing that can change a draw-framebuffer query. */
    private long framebufferBindSerial;

    /**
     * {@return how many framebuffer binds this stub has recorded}.
     * <p>
     * A caller that has to know the bound draw framebuffer can cache its answer against this number instead of asking
     * GL per draw: the answer cannot change without a bind, and the bind is what bumps the serial.
     */
    public static long celtium$framebufferBindSerial() {
        CeltiumGlStub stub = installed;
        return stub == null ? -1L : stub.framebufferBindSerial;
    }

    /**
     * Colour attachments of framebuffers created through raw GL, which is how a mod's own render target is built.
     *
     * <p>The vanilla path is already linked: {@code FramebufferMixin} reports {@code (identity, framebufferTexture)} on
     * every bind. A mod that calls {@code glGenFramebuffers}/{@code glFramebufferTexture2D} itself has no such object,
     * so nothing said which texture its draws go into - and the 268-mod E2E pack's refusals are exactly that shape:
     * 62 batches per run reading {@code texture=506 (no mirror for this GL id)} with
     * {@code the bound texture could not be mirrored=3} beside it. Routing the pair here is what makes that
     * association exist at all; the resolution itself stays where it already is
     * ({@code celtium$publishFramebufferColorTexture}).
     */
    // GAP-ST-3 rewired: the five attachment records (colour forward/reverse, per-slot colour,
    // depth forward/reverse) are GlsmFramebufferAttachments'. The javadoc above is the record's
    // history; the resolution stays at celtium$publishFramebufferColorTexture as it says.

    /** Colour attachment slots a framebuffer record carries: the MRT ledger size (GL_MAX_COLOR_ATTACHMENTS). */
    static final int COLOR_ATTACHMENT_SLOTS = 8;

    /** GL_COLOR_ATTACHMENT0; slot i of a framebuffer record is the texture attached at GL_COLOR_ATTACHMENT0+i. */
    private static final int COLOR_ATTACHMENT0 = 0x8CE0;

    /**
     * Every colour attachment of a raw framebuffer, by slot, mirroring slot 0 of {@link #framebufferColorTextures}.
     * A mod's render target can attach up to {@link #COLOR_ATTACHMENT_SLOTS} colour textures and write several in
     * one pass (Iris's gbuffer targets attach colortex0/1/2 at once); the single-texture record above cannot say
     * that, so the tuple lives here and the backend's publication record is filled from it.
     */

    /**
     * Depth attachments of raw framebuffers, mirroring {@link #framebufferColorTextures} for the depth slot.
     * <p>
     * The colour record above is what lets a sampled colour texture resolve to the image its framebuffer's draws
     * went into; depth attachments had no record at all, so a depth texture's name resolved to nothing the
     * renderer owns and every depth copy out of a mod framebuffer was refused ("framebuffer N is not an image
     * this renderer owns" — the Iris shadow pre-translucent depth copy is the measured case, legs 11-13). GL
     * keys depth storage by the texture, not by the framebuffer — several framebuffers share one depth texture
     * (every Iris shadow framebuffer attaches the same mainDepth) — so this record is the fact the backend's
     * shared depth store is keyed from.
     */

    /** GL_DEPTH_ATTACHMENT. */
    private static final int DEPTH_ATTACHMENT = 0x8D00;

    /** GL_DEPTH_STENCIL_ATTACHMENT: its depth half is the same storage this record tracks. */
    private static final int DEPTH_STENCIL_ATTACHMENT = 0x821A;

    /** {@return whether an attachment enum names a framebuffer's depth storage}. */
    static boolean isDepthAttachment(int attachment) {
        return attachment == DEPTH_ATTACHMENT || attachment == DEPTH_STENCIL_ATTACHMENT;
    }

    /**
     * Records one depth attachment; texture 0 detaches, exactly as {@link #updateColorAttachment} does for
     * colour. Kept static over the maps so the rule is unit-testable without a stub instance.
     */
    static void updateDepthAttachment(it.unimi.dsi.fastutil.ints.Int2IntMap forward,
            it.unimi.dsi.fastutil.ints.Int2IntMap reverse, int framebuffer, int attachment, int texture) {
        if (framebuffer == 0 || !isDepthAttachment(attachment)) {
            return;
        }
        updateColorAttachment(forward, reverse, framebuffer, texture);
    }

    /** OpenGL texture 0 detaches GL_COLOR_ATTACHMENT0, never leaves its previous image alive. */
    /** {@return the slot a GL attachment enum names} (0 for GL_COLOR_ATTACHMENT0), or -1 when it names no colour slot. */
    static int colorAttachmentSlot(int attachment) {
        int slot = (attachment & 0xFFFF) - COLOR_ATTACHMENT0;
        return slot >= 0 && slot < COLOR_ATTACHMENT_SLOTS ? slot : -1;
    }

    /**
     * Records one colour attachment slot of a framebuffer; texture 0 detaches the slot, and a framebuffer whose
     * slots are all detached drops out of the map. Kept as a static helper over the map, like
     * {@link #updateColorAttachment}, so the rule is unit-testable without a stub instance.
     */
    static void updateColorAttachmentSlot(it.unimi.dsi.fastutil.ints.Int2ObjectMap<int[]> slots,
            int framebuffer, int slot, int texture) {
        if (framebuffer == 0 || slot < 0 || slot >= COLOR_ATTACHMENT_SLOTS) {
            return;
        }
        int[] recorded = slots.get(framebuffer);
        if (recorded == null) {
            if (texture == 0) {
                return;
            }
            recorded = new int[COLOR_ATTACHMENT_SLOTS];
            slots.put(framebuffer, recorded);
        }
        recorded[slot] = texture;
        for (int value : recorded) {
            if (value != 0) {
                return;
            }
        }
        slots.remove(framebuffer);
    }

    static void updateColorAttachment(it.unimi.dsi.fastutil.ints.Int2IntMap forward,
            it.unimi.dsi.fastutil.ints.Int2IntMap reverse, int framebuffer, int texture) {
        if (framebuffer == 0) return;
        // Zero is the maps' absent value: a colour attachment is never stored with texture 0.
        int previous = forward.remove(framebuffer);
        if (previous != 0 && reverse.get(previous) == framebuffer) {
            reverse.remove(previous);
        }
        if (texture != 0) {
            forward.put(framebuffer, texture);
            reverse.put(texture, framebuffer);
        }
    }

    private void forgetFramebuffer(int name) {
        if (name == 0) return;
        int color = GlsmFramebufferAttachments.current().colorTexture(name);
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            vulkan.celtium$rawFramebufferDeleted(name, color);
        }
        GlsmFramebufferAttachments.current().framebufferDeleted(name);
        if (this.drawFbo == name) {
            this.drawFbo = 0;
            this.framebufferBindSerial++;
        }
        if (this.readFbo == name) this.readFbo = 0;
    }

    /** The attachment record {@code glFramebufferTexture2D} writes and the backend reads. */
    private void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        int framebuffer = target == 0x8CA8 ? this.readFbo : this.drawFbo;
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
            GlsmFramebufferAttachments.current().attachDepth(framebuffer, texture);
            if (texture != 0 && framebufferDepthAttachmentLogs < 8) {
                framebufferDepthAttachmentLogs++;
                System.out.println("[Celtium] CELTIUM_RAW_FBO_DEPTH framebuffer=" + framebuffer
                        + " depthTexture=" + texture
                        + " depthAttachments=" + GlsmFramebufferAttachments.current().depthTextureCount());
            }
            // The depth attach completes most framebuffers' records (colour went on first), so it is a
            // registration point too: a framebuffer whose binds never reach the renderer's bind report still
            // gets its identity, pair and shared-depth borrow from here.
            Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
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
        GlsmFramebufferAttachments.current().attachColor(framebuffer, slot, texture);
        // After the record update, not before: the backend's registration reads the record this call just wrote
        // (it used to be notified first and so registered the previous attachment, or none).
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            vulkan.celtium$rawAttachmentChanged(framebuffer);
        }
        if (texture == 0) return;
        if (framebufferAttachmentLogs < 8) {
            framebufferAttachmentLogs++;
            System.out.println("[Celtium] CELTIUM_RAW_FBO framebuffer=" + framebuffer + " slot=" + slot
                    + " colorTexture=" + texture
                    + " textarget=0x" + Integer.toHexString(textarget) + " level=" + level
                    + " colorAttachments=" + GlsmFramebufferAttachments.current().colorTextureCount());
        }
    }

    /** Bounded, so a mod that rebuilds its framebuffer every frame cannot flood the log. */
    private static int framebufferAttachmentLogs;

    /** Bounded companion to {@link #framebufferAttachmentLogs} for the depth record. */
    private static int framebufferDepthAttachmentLogs;

    /** Attachments refused for naming a mip level the record does not hold: counted, so the loss is visible. */
    private static int framebufferLevelRefusals;

    /** Bounded companion to {@link #framebufferAttachmentLogs} for the level refusal. */
    private static int framebufferLevelRefusalLogs;

    /**
     * One shared pair for every extent answer. The only caller reads width and height immediately and keeps neither;
     * this runs on each raw framebuffer bind, where a fresh int[2] per bind was pure garbage.
     */
    private static final int[] TEXTURE_EXTENT_SCRATCH = new int[2];

    /** {@return a texture's level-0 width and height as GL last defined them}, or null when nothing was recorded. */
    public static int[] celtium$textureExtent(int glTextureName) {
        CeltiumGlStub stub = installed;
        if (stub == null || glTextureName == 0) {
            return null;
        }
        int[] level = stub.texLevels.get(texLevelKey(glTextureName, 0));
        if (level == null) {
            return null;
        }
        TEXTURE_EXTENT_SCRATCH[0] = level[0];
        TEXTURE_EXTENT_SCRATCH[1] = level[1];
        return TEXTURE_EXTENT_SCRATCH;
    }

    /** {@return the draw framebuffer the stub last bound}, or -1 when no stub is installed. */
    public static int celtium$drawFramebuffer() {
        CeltiumGlStub stub = installed;
        return stub == null ? -1 : stub.drawFbo;
    }

    /** {@return the read framebuffer the stub last bound}, or -1 when no stub is installed. */
    public static int celtium$readFramebuffer() {
        CeltiumGlStub stub = installed;
        return stub == null ? -1 : stub.readFbo;
    }

    /**
     * {@return whether the stub's current draw framebuffer is the game's main one}, true when no stub is installed.
     * <p>
     * The clear-ordering flush asks this before it decides to open a window: a main-framebuffer clear is carried by the
     * frame latch and applied at the next acquire, so the draws it must erase have to reach the image first, while a
     * non-main clear can ride the draw queue as a marker on the last group of its own target.
     */
    public static boolean celtium$drawFramebufferIsMain() {
        CeltiumGlStub stub = installed;
        return stub == null || stub.drawFramebufferIsMain;
    }

    /** {@return the colour texture of a raw framebuffer, or 0 when none was recorded}. */
    public static int celtium$framebufferColorTexture(int glFramebufferName) {
        CeltiumGlStub stub = installed;
        if (stub == null || glFramebufferName == 0) {
            return 0;
        }
        return GlsmFramebufferAttachments.current().colorTexture(glFramebufferName);
    }

    /** {@return the depth texture of a raw framebuffer, or 0 when none was recorded}. */
    public static int celtium$framebufferDepthTexture(int glFramebufferName) {
        CeltiumGlStub stub = installed;
        if (stub == null || glFramebufferName == 0) {
            return 0;
        }
        return GlsmFramebufferAttachments.current().depthTexture(glFramebufferName);
    }

    /** {@return the framebuffer a depth texture is attached to, or 0 when the texture is not a raw depth attachment}. */
    public static int celtium$framebufferForDepthTexture(int glTextureName) {
        CeltiumGlStub stub = installed;
        if (stub == null || glTextureName == 0) {
            return 0;
        }
        return GlsmFramebufferAttachments.current().framebufferForDepthTexture(glTextureName);
    }

    /**
     * {@return a copy of a raw framebuffer's colour attachments by slot} (index 0 is GL_COLOR_ATTACHMENT0, 0 marks
     * a detached slot), always {@link #COLOR_ATTACHMENT_SLOTS} long. The copy is what the hint reports to the
     * backend on a raw bind; the record itself stays private to the stub.
     */
    public static int[] celtium$framebufferColorAttachments(int glFramebufferName) {
        int[] out = new int[COLOR_ATTACHMENT_SLOTS];
        CeltiumGlStub stub = installed;
        if (stub == null || glFramebufferName == 0) {
            return out;
        }
        GlsmFramebufferAttachments.current().colorAttachments(glFramebufferName, out);
        return out;
    }

    /** {@return the framebuffer a colour texture belongs to, or 0 when the texture is not a raw attachment}. */
    public static int celtium$framebufferForColorTexture(int glTextureName) {
        CeltiumGlStub stub = installed;
        if (stub == null || glTextureName == 0) {
            return 0;
        }
        return GlsmFramebufferAttachments.current().framebufferForColorTexture(glTextureName);
    }

    /** {@return whether {@code framebuffer}'s recorded colour attachments name {@code glTextureName} at any slot}. */
    private static boolean celtium$framebufferAttachesColor(CeltiumGlStub stub, int framebuffer,
            int glTextureName) {
        return GlsmFramebufferAttachments.current().attachesColor(framebuffer, glTextureName);
    }

    /**
     * The one capability producer: {@code glEnable}/{@code glDisable} and their client-state spellings all land here.
     *
     * <p>Vanilla sets {@code GL_TEXTURE_2D} this way before it draws, and the stub's draw path reads the record to
     * decide whether the batch samples a texture at all. Routing {@code glEnable}/{@code glDisable} through here is
     * what fixed the clouds: measured on leg gv-cohort-cloud9, the cloud VBO draw carried
     * {@code texture2dEnabled=false} while its own attribution line showed the correct UVs, so the plane was drawn
     * untextured - uniform slabs at the cloud colour instead of white clouds.
     */
    private void setCapability(int capability, boolean enabled) {
        if (capability == GL_SCISSOR_TEST) {
            this.scissorToggleCount++;
        }
        if (capability < 0 || capability >= FLAT_MAP_SIZE) {
            return;
        }
        switch (capability) {
            case GL_FOG: org.soutalab.celtium.vulkan.ImmediateFogState.current().enabled(enabled); break;
            // The capabilities whose state the immediate pipeline reads from the backend's mirror. Vanilla reaches them
            // through GlStateManager and its mixin; a mod's raw glEnable/glDisable reached only the enables array, so
            // the pipeline kept the previous state - a raw glDisable(GL_DEPTH_TEST) overlay was still depth-tested.
            case GL_DEPTH_TEST: {
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                if (backend != null) backend.setDepthTest(enabled);
                break;
            }
            case GL_ALPHA_TEST: {
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                if (backend != null) backend.setAlphaTestEnabled(enabled);
                break;
            }
            case 0x0B50: { // GL_LIGHTING
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                if (backend != null) backend.setLightingEnabled(enabled);
                break;
            }
            case GL_BLEND: this.raster.blendEnabled(enabled); break;
            case GL_CULL_FACE: this.raster.cullEnabled(enabled); break;
            case GL_SCISSOR_TEST: this.raster.scissorTest(enabled); break;
            case GL_POLYGON_OFFSET_FILL: this.raster.polygonOffsetFillEnabled(enabled); break;
            case GL_TEXTURE_2D: this.textures.enabled2d(enabled); break;
            default:
                if (GlsmEnableState.current().enabled(capability) != enabled && isDrawStateCapability(capability)) noteDrawStateBoundary();
                break;
        }
        GlsmEnableState.current().set(capability, enabled);
    }

    static boolean isProxyTarget(int target) {
        // GL_PROXY_TEXTURE_1D/2D/3D/CUBE_MAP - uploads to these carry no pixels and bind to nothing.
        return target == 0x8063 || target == 0x8064 || target == 0x8070 || target == 0x851B;
    }

    long texBindKey(int target) {
        return ((long) this.textures.activeUnit() << 16) | (target & 0xFFFFL);
    }

    /**
     * The shared record key: texture name in the high bits, level in the low byte. {@link #texLevels} and
     * {@link #texCaptures} agree on it, so a level's extent and its pixels are found the same way.
     */
    /** Highest low-byte mip level written to texLevels/texCaptures/texPatchBacklog so forgetTexture avoids 256-probes. */
    private int maxRecordedTexLevel = -1;

    static long texLevelKey(int name, int level) {
        return ((long) name << 16) | (level & 0xFFL);
    }

    long putTexLevelKey(int name, int level) {
        int masked = level & 0xFF;
        if (masked > this.maxRecordedTexLevel) {
            this.maxRecordedTexLevel = masked;
        }
        return ((long) name << 16) | masked;
    }

    /**
     * The name an upload's records belong to: the texture bound to the target on the active unit, or {@code -target}
     * for a proxy target. Proxy uploads are how FML probes the maximum texture size - they bind to nothing and their
     * "pixels" are never a real image, so only an extent is recorded for them.
     */
    int uploadTargetName(int target) {
        if (isProxyTarget(target)) {
            return -target;
        }
        int bound = (target == GL_TEXTURE_2D ? this.textures.texture2d() : this.textureBindings.get(texBindKey(target)));
        if (bound < 0) {
            // The per-unit map is empty under GL-less, by design: GlStateManager.bindTexture skips the GL call when its
            // own cache already holds the binding, so no glBindTexture arrives for the unit a texture was first bound
            // on. The texture parameters and the texture copies already fall back to the last binding the state manager
            // named; an upload has the same need, and it is what gives the block atlas its pixels. Measured on the
            // 268-mod E2E pack: the 8192x8192 atlas is GL texture 353 and the registry logged
            // CELTIUM_CAPTURE_MISS glTexture=353 level=0 "the stub captured no upload for this texture level" while
            // nglTexSubImage2D was called 41199 times in a single cohort run - the uploads went nowhere because the
            // unit map had no entry to file them under.
            bound = lastBoundTexture;
        }
        return bound < 0 ? 0 : bound;
    }

    /**
     * Records an uploaded extent under {@link #uploadTargetName}, which is what {@code glGetTexLevelParameter} answers
     * from. For an upload whose pixels the stub cannot use, this record is all there is.
     */
    void recordExtent(int target, int level, int internal, int width, int height) {
        int name = uploadTargetName(target);
        if (name == 0) {
            return;
        }
        recordExtentForName(name, level, internal, width, height);
    }

    /** The extent record for a texture already named, as the DSA storage forms carry it. */
    private void recordExtentForName(int name, int level, int internal, int width, int height) {
        int[] previous = this.texLevels.put(putTexLevelKey(name, level), new int[] { width, height, internal });
        if (TARGET_FORMAT_CENSUS && level == 0 && internal != 0 && !isDepthInternalFormat(internal)) {
            synchronized (this.targetFormatRequests) {
                if (this.targetFormatRequests.containsKey(internal)
                        || this.targetFormatRequests.size() < MAX_TARGET_FORMATS) {
                    this.targetFormatRequests.addTo(internal, 1L);
                }
            }
        }
        if (level == 0 && previous != null && (previous[0] != width || previous[1] != height)
                && previous[0] * previous[1] >= 1024 * 1024 && RESPECIFIED_LINES.getAndDecrement() > 0) {
            // A large level re-specified at another size: the texture name was handed to something else (a mod that
            // deleted and re-generated it, or one that uploaded while it believed its own texture was bound). The
            // mirror under that name is then replaced, so this is the line that names who took a block atlas's name.
            StringBuilder callers = new StringBuilder();
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 2; i < stack.length && i < 14; i++) {
                callers.append(i == 2 ? "" : " <- ").append(stack[i].getClassName().replaceAll(".*\\.", ""))
                        .append('.').append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
            }
            System.out.println("[Celtium] CELTIUM_TEXTURE_RESPECIFIED name=" + name + " " + previous[0] + "x"
                    + previous[1] + " -> " + width + "x" + height + " callers=[" + callers + "]");
        }
    }

    /** Bounded: a run reports the first few large re-specifications. */
    private static final java.util.concurrent.atomic.AtomicInteger RESPECIFIED_LINES =
            new java.util.concurrent.atomic.AtomicInteger(8);

    /**
     * {@code glTexStorage2D/3D}: storage is reserved for level 0 (the level count is not walked) and its content is
     * undefined even in real GL, so there is nothing to capture - only the extent the size queries answer from.
     */
    private void texImage(long[] c) {
        recordExtent((int) c[0], 0, (int) c[2], (int) c[3], (int) c[4]);
    }

    /** Writes into the storage {@code glBufferData} allocated, which is what the mirrored meshes are read from. */
    private void bufferSubData(Fn fn, long[] c) {
        bufferSubDataFor(bufferFor(fn, c), c[1], c[2], c[3]);
    }

    /** The one sub-data producer: the dispatch case, the DSA form and the transformer's routes land here. */
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
        int logicalSize = this.logicalSizeOf(name, storage == null ? 0 : storage.capacity());
        if (storage == null || data == 0L || mapRangeOffset(offset, size, logicalSize) < 0L) {
            return;
        }
        ByteBuffer source = MemoryUtil.memByteBuffer(data, (int) size);
        int position = storage.position();
        storage.position((int) offset);
        storage.put(source);
        storage.position(position);
    }

    /** {@code glPixelStorei}: the unpack state upload conversion reads rows with. Only the UNPACK_* pnames matter. */
    private void pixelStore(int pname, int value) {
        GlsmBufferState.current().pixelStore(pname, value);
    }

    // ---- texture pixels: the uploads the Vulkan mirrors are built from -----------------------------------
    //
    // Extracted (giant-split seam 7, QUEUE 688): the capture bodies moved to GlTextureCaptures, which owns
    // them now - the filed records and their census, the patch backlog's bookkeeping, the RGBA8 conversion
    // and the announced-patch skip. The three stores the captures file into stay fields of this stub and
    // are handed over at construction (see the textureCaptures field), because forgetTexture and the
    // copy-image path read them beside the captures' own state. The doors below keep the dispatch and
    // transformer call sites unchanged.

    /** The pixel formats and types the conversion understands - the pairs the game's texture loader uses. */
    static final int GL_UNSIGNED_BYTE = 0x1401;
    static final int GL_RGB = 0x1907;
    static final int GL_RGBA = 0x1908;
    static final int GL_BGRA = 0x80E1;

    /** The tex-image producer the dispatch case lands on; see {@link GlTextureCaptures}. */
    private void texImagePixels(Fn fn, long[] c) {
        this.textureCaptures.texImagePixels(this, fn, c);
    }

    /** The tex-image producer the transformer's route lands on; see {@link GlTextureCaptures}. */
    private void texImagePixels(String name, long[] c) {
        this.textureCaptures.texImagePixels(this, name, c);
    }

    /** The one tex-image producer; see {@link GlTextureCaptures}. */
    private void texImagePixels(String name, int target, int level, int internal, int width, int height, int border,
            int format, int type, long pixels) {
        this.textureCaptures.texImagePixels(this, name, target, level, internal, width, height, border, format,
                type, pixels);
    }

    /** The patch producer the dispatch case lands on; see {@link GlTextureCaptures}. */
    private void texSubImage(long[] c) {
        this.textureCaptures.texSubImage(this, c);
    }

    /** The one tex-sub-image producer; see {@link GlTextureCaptures}. */
    private void texSubImage(int target, int level, int xOffset, int yOffset, int width, int height, int format,
            int type, long pixels) {
        this.textureCaptures.texSubImage(this, target, level, xOffset, yOffset, width, height, format, type,
                pixels);
    }

    /** The DSA spelling's producer; see {@link GlTextureCaptures}. */
    private void texSubImageForName(int name, int level, int xOffset, int yOffset, int width, int height, int format,
            int type, long pixels) {
        this.textureCaptures.texSubImageForName(this, name, level, xOffset, yOffset, width, height, format, type,
                pixels);
    }

    /** Files one converted RGBA8 patch into a level's captured rows; see {@link GlTextureCaptures}. */
    private void fileTexturePatch(int name, int level, int x, int y, int width, int height, ByteBuffer patch) {
        this.textureCaptures.fileTexturePatch(this, name, level, x, y, width, height, patch);
    }

    /** A level allocated with a NULL data pointer; see {@link GlTextureCaptures}. */
    private void undefinedLevel(int name, int level, int width, int height, int depth) {
        this.textureCaptures.undefinedLevel(this, name, level, width, height, depth);
    }

    /** {@code glCompressedTexImage2D}: the extent record only; see {@link GlTextureCaptures}. */
    private void compressedTexImage(long[] c) {
        this.textureCaptures.compressedTexImage(this, c);
    }

    /** {@code bytes} rounded up to a GL unpack alignment; see {@link GlTextureCaptures}. */
    private static int aligned(int bytes, int alignment) {
        return GlTextureCaptures.aligned(bytes, alignment);
    }

    /** Whether a texture upload's {@code pixels} argument names data; see {@link GlTextureCaptures}. */
    static boolean carriesPixels(long pixels, int unpackBuffer) {
        return GlTextureCaptures.carriesPixels(pixels, unpackBuffer);
    }

    /** Converts one upload's pixels to RGBA8; see {@link GlTextureCaptures}. */
    private ByteBuffer uploadToRgba8(int width, int height, int format, int type, long pixels) {
        return this.textureCaptures.uploadToRgba8(this, width, height, format, type, pixels);
    }

    /** Arms the one-shot skip for the patch an announcement just applied; see {@link GlTextureCaptures}. */
    public static void celtium$noteAnnouncedPatch(int level, int x, int y, int width, int height) {
        GlTextureCaptures.celtium$noteAnnouncedPatch(level, x, y, width, height);
    }

    /** {@return how many captures the announcement made unnecessary} */
    public static long celtium$announcedCaptureSkips() {
        return GlTextureCaptures.celtium$announcedCaptureSkips();
    }

    /**
     * Records TextureUtil's named allocation after its delete/bind/allocate sequence. The dimensions describe
     * storage, not uploaded content: an undefined surface becomes usable only when an actual write is announced.
     * The mip argument is the maximum level, not a level count, just like TextureUtil.allocateTextureImpl.
     */
    public static void recordTextureAllocation(int name, int mipmapLevels, int width, int height) {
        CeltiumGlStub stub = installed;
        if (stub == null || name <= 0 || mipmapLevels < 0 || mipmapLevels > 31 || width < 0 || height < 0) {
            return;
        }
        stub.forgetTexture(name);
        for (int level = 0; level <= mipmapLevels; level++) {
            int levelWidth = width >> level;
            int levelHeight = height >> level;
            stub.texLevels.put(stub.putTexLevelKey(name, level), new int[] { levelWidth, levelHeight, GL_RGBA });
            stub.undefinedLevel(name, level, levelWidth, levelHeight, 1);
        }
    }

    /** Deletes the named storage record too when native glDeleteTextures cannot reach callback dispatch. */
    public static void forgetTextureAllocation(int name) {
        CeltiumGlStub stub0 = installed;
        if (stub0 != null) {
            GlsmNameLedger.current().removeNullAllocated(name);
        }
        CeltiumGlStub stub = installed;
        if (stub != null && name > 0) {
            stub.deleteTexture(name);
        }
    }

    /**
     * A named texture-level query against the GL-less record, without crossing an LWJGL GL45C
     * native entry point. The value is exactly what our DSA stub function would answer;
     * a texture with no level record returns 0 instead of inventing dimensions, and a query
     * before the stub is installed answers 0 as well.
     */
    public static int textureLevelParameter(int name, int level, int pname) {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : stub.texLevelParamForName(name, level, pname);
    }

    /** The pname's recorded value for a named texture's level - the DSA form of {@link #texLevelParam}. */
    private int texLevelParamForName(int name, int level, int pname) {
        int[] dims = this.texLevels.get(texLevelKey(name, level));
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
            default:
                return 8; // the component sizes the announced formats describe themselves with
        }
    }

    /**
     * The recorded extent of a named texture's level, in one lookup: {@code out[0]} is the width and {@code out[1]}
     * the height.
     * <p>
     * The accept path asks for both on every offer, and they are one record - {@code texLevels} is keyed by
     * (name, level) - so the two {@link #textureLevelParameter} calls hashed the same key twice. This is the same
     * answer with one hash. A level with no record answers {@code false} and leaves {@code out} alone, which is what
     * the caller's "no extent" test needs; a query before the stub is installed answers {@code false} too.
     */
    public static boolean textureLevelExtent(int name, int level, int[] out) {
        CeltiumGlStub stub = installed;
        return stub != null && stub.texLevelExtentForName(name, level, out);
    }

    private boolean texLevelExtentForName(int name, int level, int[] out) {
        int[] dims = this.texLevels.get(texLevelKey(name, level));
        if (dims == null) {
            return false;
        }
        out[0] = dims[0];
        out[1] = dims[1];
        return true;
    }

    /** The pname's recorded value for the bound texture's level, or 0 when nothing was uploaded. */
    private int texLevelParam(int target, int level, int pname) {
        int name = uploadTargetName(target);
        int[] dims = this.texLevels.get(texLevelKey(name, level));
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
            // GL_TEXTURE_RED/GREEN/BLUE/ALPHA/LUMINANCE/INTENSITY_SIZE. The previous range (0x803C..0x8041) named
            // internal formats - GL_ALPHA8 and friends - which are not query pnames, so a real channel-size query
            // returned 0.
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

    // ---- buffer storage: the minimum that keeps upload/mapping code paths alive ------------------------

    /**
     * The allocation, logical-size and mapping semantics live in {@link BufferStorageRecord}, which is handed
     * this stub's store map at construction (the map stays a field of this stub: the pixel-buffer,
     * texture-upload and draw paths read it directly). What stays in this section is the binding resolution
     * that names the buffer a call applies to and the delegations the dispatch, the transformer's route and
     * the census land on.
     */
    private final BufferStorageRecord bufferStorage = new BufferStorageRecord(GlsmBufferState.current().store());

    /**
     * The buffer whose storage an operation applies to: the bound name for {@code glBufferData}, or the name arg
     * itself for {@code glNamedBufferData}.
     */
    int boundBuffer(int target) {
        // The pixel buffers keep their own binding fields (celtium$bindBuffer stores them there), so a buffer-data or
        // map call against GL_PIXEL_UNPACK_BUFFER has to read them here; it used to read the generic table, which never
        // holds them, and dropped Xaero's Minimap's PBO allocation on the floor.
        if (target == GL_PIXEL_UNPACK_BUFFER) {
            return this.pixelUnpackBuffer;
        }
        if (target == GL_PIXEL_PACK_BUFFER) {
            return this.pixelPackBuffer;
        }
        return target == GL_ARRAY_BUFFER ? NativeGeometryBuffer.boundId()
                : GlsmBufferState.current().bound(target);
    }

    private int bufferFor(Fn fn, long[] c) {
        return fn.namedObject ? (int) c[0] : boundBuffer((int) c[0]);
    }

    private void bufferData(Fn fn, long[] c) {
        bufferDataFor(bufferFor(fn, c), c[1], c[2]);
    }

    /** The one buffer-data producer: the dispatch case and the transformer's route both land here. */
    private void bufferDataFor(int name, long size, long data) {
        this.bufferStorage.bufferDataFor(name, size, data);
    }

    /** {@return the size GL last gave this buffer}; the semantics live in {@link BufferStorageRecord#logicalSizeOf}. */
    int logicalSizeOf(int name, int capacity) {
        return this.bufferStorage.logicalSizeOf(name, capacity);
    }

    /** The range contract lives in {@link BufferStorageRecord#mapRangeOffset}; this door keeps its callers unchanged. */
    static long mapRangeOffset(long offset, long length, long capacity) {
        return BufferStorageRecord.mapRangeOffset(offset, length, capacity);
    }

    /** {@return the map calls and how many were refused for an out-of-range request}, for the verification line. */
    public static String mapBufferCensus() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "calls=none refusals=none";
        }
        return stub.bufferStorage.census();
    }

    private long mapBuffer(Fn fn, long[] c) {
        boolean range = fn.args.length == 4;
        return this.bufferStorage.mapBufferFor(bufferFor(fn, c), range, range ? c[1] : 0L, range ? c[2] : 0L, range ? c[3] : 0L);
    }

    /**
     * {@code glMapBuffer}/{@code glMapBufferRange} from the native transformer's route: LWJGL's {@code nglMapBuffer}
     * used to fall through to the default zero, which LWJGL hands the caller as a null buffer - Xaero's Minimap then
     * crashed on its first tile upload ({@code PixelBuffers.glMapBuffer(...) is null}).
     */
    public static long celtium$mapBuffer(int target, boolean range, long offset, long length, int access) {
        CeltiumGlStub stub = installed;
        return stub == null ? 0L : stub.bufferStorage.mapBufferFor(stub.boundBuffer(target), range, offset, length, access);
    }

    /** {@code glUnmapBuffer}: the storage is the caller's memory already, so there is nothing to flush. */
    public static boolean celtium$unmapBuffer(int target) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return true;
        }
        return BufferStorageRecord.unmap(stub.boundBuffer(target));
    }

    /** The DSA mapping forms: the buffer is named, not the bound target. */
    public static long celtium$mapNamedBuffer(int name, boolean range, long offset, long length, int access) {
        CeltiumGlStub stub = installed;
        return stub == null ? 0L : stub.bufferStorage.mapBufferFor(name, range, offset, length, access);
    }

    public static boolean celtium$unmapNamedBuffer(int name) {
        return BufferStorageRecord.unmap(name);
    }

    // ---- client-array drawing: the one GL drawing entry point that leaves this class for the frame ---------------

    // ---- texture environment, per texture unit -------------------------------------------------------------------
    //
    // GL keeps one environment per unit; vanilla's hurt flash switches to the lightmap unit and sets an INTERPOLATE
    // combine with a constant colour there, then asks it back when it builds the immediate batch's combine state. The
    // stub records what the game set instead of answering from GL, which under GL-less has nothing to answer from.
    private static final int GL_TEXTURE_ENV = 0x2300;
    private static final int GL_TEXTURE_ENV_COLOR = 0x2201;
    private void celtium$setTexEnvInt(int target, int pname, int param) {
        org.soutalab.celtium.vulkan.NativeTextureEnvironment.current().parameter(target, pname, param);
    }

    private void celtium$setTexEnvFloats(int target, int pname, long values) {
        if (values == 0L || target != GL_TEXTURE_ENV) return;
        org.soutalab.celtium.vulkan.NativeTextureEnvironment env =
                org.soutalab.celtium.vulkan.NativeTextureEnvironment.current();
        if (pname == GL_TEXTURE_ENV_COLOR) {
            env.color(target, pname, MemoryUtil.memGetFloat(values), MemoryUtil.memGetFloat(values + 4),
                    MemoryUtil.memGetFloat(values + 8), MemoryUtil.memGetFloat(values + 12));
        } else {
            env.parameter(target, pname, Math.round(MemoryUtil.memGetFloat(values)));
        }
    }

    private void celtium$getTexEnvInt(int target, int pname, long out) {
        if (out != 0L) celtium$putInt(out,
                org.soutalab.celtium.vulkan.NativeTextureEnvironment.current().activeParameter(target, pname));
    }

    private void celtium$getTexEnvFloats(int target, int pname, long out) {
        if (out == 0L || target != GL_TEXTURE_ENV) return;
        org.soutalab.celtium.vulkan.NativeTextureEnvironment env =
                org.soutalab.celtium.vulkan.NativeTextureEnvironment.current();
        if (pname == GL_TEXTURE_ENV_COLOR) {
            for (int i = 0; i < 4; i++) MemoryUtil.memPutFloat(out + i * 4L, env.colorComponent(i));
        } else {
            MemoryUtil.memPutFloat(out, env.activeParameter(target, pname));
        }
    }

    // ---- texture parameters, per texture object ----------------------------------------------------------------
    //
    // The record itself - the recorded values, their per-name versions, the bounded census and the query
    // instrument - lives in TextureParameterRecord. What stays here is the binding resolution that names the
    // texture a non-DSA call applies to (it reads this class's binding state), the call-site guard the dispatch
    // feeds the record's instrument with, and the static doors the mirror reads through, so the record's
    // consumers keep the signatures they already use.
    private final TextureParameterRecord textureParameters = new TextureParameterRecord();

    /** {@return how many times this texture's recorded parameters changed}, 0 when the game never set one. */
    public static int textureParameterVersion(int textureName) {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : stub.textureParameters.version(textureName);
    }

    /**
     * The call site for a parameter set, or the empty string once the instrument has named its shapes.
     * <p>
     * Reading the stack is the expensive part of this instrument, and a texture load sets four parameters per texture,
     * so it is taken only while the bounded shape set is still being filled.
     */
    private static String celtium$paramSite() {
        return org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(TextureParameterRecord.paramShapeCount(), 24)
                ? celtium$callSite(5) : "";
    }

    /** The first element of an array-valued texture parameter: every scalar pname this record carries is one wide. */
    private static int celtium$firstParamValue(Fn fn, long values) {
        if (values == 0L) {
            return 0;
        }
        return fn.name.endsWith("fv") ? Math.round(MemoryUtil.memGetFloat(values)) : MemoryUtil.memGetInt(values);
    }

    /** Records one texture parameter on this stub's record; see {@link TextureParameterRecord#set}. */
    private void celtium$setTextureParameter(int textureName, int pname, int value, String site) {
        this.textureParameters.set(textureName, pname, value, site);
    }

    /** The non-DSA form: the texture bound to {@code target} on the active unit is the one the call applies to. */
    private void celtium$setTextureParameterBound(int target, int pname, int value, String site) {
        if (isProxyTarget(target)) {
            // A proxy target carries no image and binds to nothing; GL accepts the call and it means nothing.
            return;
        }
        int bound = (target == GL_TEXTURE_2D ? this.textures.texture2d() : this.textureBindings.get(texBindKey(target)));
        if (bound < 0) {
            // Under GL-less the per-unit map is empty by design: the bind is rewritten before any trampoline sees it
            // (see celtium$noteBoundTexture), so the record is asked for the name the manager last bound. Without this
            // the parameter was filed against texture 0 and the registry never saw a filter - measured as the blurred
            // item: it falls back to inferring LINEAR from the upload's shape when nothing is recorded.
            bound = this.lastBoundTexture;
        }
        this.textureParameters.set(bound < 0 ? 0 : bound, pname, value, site);
    }

    /**
     * The particle batch's own texture coordinates, for the first few accepted batches (task-54).
     * <p>
     * The question this answers is whether the sprite a particle asked for survives into the vertices: a
     * {@code Particle} turns its {@code particleTextureIndexX/Y} into UVs in
     * {@code Particle.renderParticle} ({@code index/16} .. {@code index/16 + 0.0624375}), so the batch's tex coords
     * are the whole sprite selection - there is no separate index to lose. Printing the first vertices' UVs and the
     * sprite cell they name tells "the glyph index arrived" from "the path substituted a default sprite" without
     * needing to see the pixels, and the sprite cell is printed as the 16x16 grid position the particle atlas uses.
     */
    private static final java.util.concurrent.atomic.AtomicInteger PARTICLE_UV_LINES =
            new java.util.concurrent.atomic.AtomicInteger();

    private void celtium$noteParticleTexCoords(int formatId, long address, int count) {
        if (formatId != FORMAT_PARTICLE || count <= 0 || address == 0L
                || !org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(PARTICLE_UV_LINES.get(), 6)) {
            return;
        }
        PARTICLE_UV_LINES.incrementAndGet();
        StringBuilder uvs = new StringBuilder();
        for (int i = 0; i < 4 && i < count; i++) {
            long at = address + (long) i * VERTEX_POINTER.stride + 12L;
            float u = MemoryUtil.memGetFloat(at);
            float v = MemoryUtil.memGetFloat(at + 4L);
            uvs.append(i == 0 ? "" : " ").append('(').append(celtium$round3(u)).append(',').append(celtium$round3(v))
                    .append(") cell=").append(Math.round(u * 16.0f)).append(',').append(Math.round(v * 16.0f));
        }
        System.out.println("[Celtium] CELTIUM_PARTICLE_UV count=" + count + " stride=" + VERTEX_POINTER.stride
                + " uvs=[" + uvs + "]");
    }


    /**
     * {@return the value a texture parameter query answers}: the record when the game set this pname on this
     * texture, otherwise GL's own initial state. The semantics live in {@link TextureParameterRecord#valueOrDefault};
     * this door stays because the transformer-facing query entry points above answer through it.
     */
    static int textureParameterValue(int textureName, int pname) {
        CeltiumGlStub stub = installed;
        return stub == null ? TextureParameterRecord.defaultValue(pname)
                : stub.textureParameters.valueOrDefault(textureName, pname);
    }

    /**
     * {@return the filter or wrap value recorded for this texture and pname, or -1 when the game never set it}.
     * <p>
     * This is the mirror's door to the record: {@link #textureParameter} answers GL's default for an unset pname,
     * while a caller that wants to keep its own fallback (the mirror's clamp) can tell the two apart here.
     */
    public static int recordedTextureParameter(int textureName, int pname) {
        CeltiumGlStub stub = installed;
        return stub == null ? -1 : stub.textureParameters.recordedValue(textureName, pname);
    }

    /**
     * {@return the value GL would answer for this texture's pname} - the recorded value, or GL's context default when
     * the game never set it. See {@link #recordedTextureParameter} for the recorded/absent distinction.
     */
    public static int textureParameter(int textureName, int pname) {
        return textureParameterValue(textureName, pname);
    }

    /** {@return whether the game set this pname on this texture} - false for the GL defaults. */
    public static boolean textureParameterRecorded(int textureName, int pname) {
        CeltiumGlStub stub = installed;
        return stub != null && stub.textureParameters.isRecorded(textureName, pname);
    }

    /** The named texture parameters, bounded: the record census and the query answers. */
    private static String texParamSummary() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "texture params: recorded=0 textures=0 repeat=0 clamp=0 unbound=0 overflow=0 queries=0";
        }
        return stub.textureParameters.summary();
    }

    /**
     * {@return the busiest table entries under {@link #FN_CENSUS}}, name=count pairs in descending order, bounded to
     * the top twenty so a leg's log names the trampoline's producers without a page of output.
     */
    private static String fnCensusSummary() {
        List<Fn> sorted = new ArrayList<>(TABLE);
        sorted.sort((a, b) -> Long.compare(b.calls, a.calls));
        StringBuilder text = new StringBuilder(512);
        long total = 0L;
        for (Fn fn : TABLE) {
            total += fn.calls;
        }
        text.append("total=").append(total).append(' ');
        int shown = 0;
        for (Fn fn : sorted) {
            if (fn.calls == 0L || shown >= 20) {
                break;
            }
            if (shown > 0) {
                text.append(',');
            }
            text.append(fn.name).append(':').append(fn.calls);
            shown++;
        }
        CeltiumGlStub stub = installed;
        if (stub != null && !stub.getIntegervPnames.isEmpty()) {
            List<it.unimi.dsi.fastutil.ints.Int2LongMap.Entry> pnames =
                    new ArrayList<>(stub.getIntegervPnames.int2LongEntrySet());
            pnames.sort((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()));
            text.append(" | getIntegervByPname:");
            int pnameShown = 0;
            for (it.unimi.dsi.fastutil.ints.Int2LongMap.Entry entry : pnames) {
                if (pnameShown++ >= 12) {
                    break;
                }
                text.append(' ').append(Integer.toHexString(entry.getIntKey()))
                        .append('=').append(entry.getLongValue());
            }
        }
        return text.toString();
    }

    /** One query answer, named once per distinct shape so a run's log says what the mirror actually asked for. */
    void celtium$noteTextureParameterQuery(String name, int textureName, int pname, int value, boolean recorded) {
        this.textureParameters.noteQuery(name, textureName, pname, value, recorded, () -> celtium$callSite(6));
    }

    // ---- copy-from-framebuffer: named, counted, and refused for a stated reason --------------------------------
    //
    // What the request is (measured shape): the main menu's panorama renders six 256x256 faces into the window and
    // copies each into the skybox texture, and its blur pass copies between its own targets. What it needs is pixels
    // of the framebuffer bound for reading. Under the GL-less window that framebuffer is the renderer's own image
    // (drawFbo/readFbo 0, or the shared pair), and this class has no readback of it: the only readback in the tree is
    // the presented-image dump, which writes a whole frame to a file at the swap point. So the copy is recorded and
    // refused by name here, with the destination and the source rectangle, and the *source* side is the piece another
    // component has to provide - a synchronous rectangle read of the owned image for the bound read framebuffer. When
    // that exists, this handler is where the pixels would be uploaded into the destination level's mirror, the same
    // way glTexSubImage2D already is.
    private long textureCopyRequests;
    private static final java.util.Set<String> TEXTURE_COPY_SHAPES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void celtium$noteTextureCopy(String name, int target, int level, int xoffset, int yoffset,
                                          int x, int y, int width, int height) {
        this.textureCopyRequests++;
        int dest = (target == GL_TEXTURE_2D ? this.textures.texture2d() : this.textureBindings.get(texBindKey(target)));
        if (dest < 0) {
            // The per-unit map is empty under GL-less by design (the bind is rewritten before any trampoline sees it),
            // so the destination is asked for the name the manager last bound - the same fallback the texture
            // parameters use. Without it the copy was filed against texture 0 and the panorama stayed content-less.
            dest = this.lastBoundTexture;
        }
        int destName = dest < 0 ? 0 : dest;
        // The source side lives with the frame target, which owns the image the read framebuffer names: ask it for the
        // rectangle, and only a "served" answer skips the refusal below. Both copy entry points funnel through here, so
        // this one call covers glCopyTexImage2D and glCopyTexSubImage2D alike; a foreign framebuffer, a non-zero level
        // or a stalled device comes back as a reason and is printed on the same line as before.
        String servedFrom;
        try {
            Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            servedFrom = backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                    ? vulkan.celtium$serveTextureCopy(destName, level, xoffset, yoffset, x, y, width, height,
                            this.readFbo)
                    : "refused-no-vulkan-backend";
        } catch (RuntimeException | LinkageError error) {
            servedFrom = "refused-serve-failed: " + error;
        }
        if ("served".equals(servedFrom)) {
            return;
        }
        String key = name + "/" + target + "/" + width + "x" + height + "/readFbo=" + this.readFbo;
        if (TEXTURE_COPY_SHAPES.size() < 8 && TEXTURE_COPY_SHAPES.add(key)) {
            System.out.println("[Celtium] CELTIUM_TEX_COPY " + name
                    + " target=0x" + Integer.toHexString(target) + " level=" + level
                    + " dest=" + destName
                    + " destOffset=" + xoffset + "," + yoffset
                    + " source=" + x + "," + y + " " + width + "x" + height
                    + " readFbo=" + this.readFbo + " drawFbo=" + this.drawFbo
                    + " servedFrom=" + servedFrom + " requests=" + this.textureCopyRequests
                    + " site=[" + celtium$callSite(8) + "]");
        }
    }

    // ---- copy between captured levels: glCopyImageSubData -----------------------------------------------------
    //
    // What the request is (measured shape): FoamFix's fast animation path (txFasterAnimation=2, the setting AMD
    // users are told to raise) holds every frame of an animated sprite side by side in a texture of its own and
    // copies the current frame into the atlas level each tick with ARBCopyImage.glCopyImageSubData. Both sides
    // are ordinary uploads here - the frame strip arrives through TextureUtil, so the stub's capture holds its
    // bytes - which makes this the one texture copy GL-less can serve in full: the source is a captured level,
    // not the framebuffer the copies above have to refuse. Before this route existed the call fell to the
    // no-op table, the copy vanished without a sound, and the atlas kept its first frame forever: water, lava
    // and fire froze with no crash and no log line (roam-compat's finding, lanechat #1387).
    private long copyImageRequests;
    private long copyImageServed;
    private static final java.util.Set<String> COPY_IMAGE_SHAPES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
            int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
            int width, int height, int depth) {
        this.copyImageRequests++;
        String refusal = null;
        if (srcTarget != GL_TEXTURE_2D || dstTarget != GL_TEXTURE_2D || depth != 1 || srcZ != 0 || dstZ != 0
                || width <= 0 || height <= 0) {
            refusal = "refused-shape: srcTarget=0x" + Integer.toHexString(srcTarget)
                    + " dstTarget=0x" + Integer.toHexString(dstTarget)
                    + " depth=" + depth + " srcZ=" + srcZ + " dstZ=" + dstZ
                    + " size=" + width + "x" + height
                    + " (only a 2D level-to-level copy is served)";
        }
        GlTextureCaptures.TexCapture source = refusal == null
                ? this.texCaptures.get(texLevelKey(srcName, srcLevel))
                : null;
        // A capture filed as a patch covers its level from the record's own (x, y), so the rectangle this copy
        // names in level coordinates is read at the level position minus the record's origin.
        int srcRecX = source == null ? 0 : srcX - source.x;
        int srcRecY = source == null ? 0 : srcY - source.y;
        if (refusal == null && (source == null || !source.gamePixels)) {
            refusal = "refused-source-unrecorded: src=" + srcName + " level=" + srcLevel
                    + " (the stub captured no game pixels for the source level)";
        } else if (refusal == null && (srcRecX < 0 || srcRecY < 0
                || srcRecX + width > source.width || srcRecY + height > source.height)) {
            refusal = "refused-source-outside: src=" + srcName + " level=" + srcLevel
                    + " rect=" + srcX + "," + srcY + " " + width + "x" + height
                    + " record=" + source.width + "x" + source.height + " at " + source.x + "," + source.y;
        }
        if (refusal != null) {
            String key = refusal.substring(0, refusal.indexOf(':')) + "/" + width + "x" + height;
            if (COPY_IMAGE_SHAPES.size() < 8 && COPY_IMAGE_SHAPES.add(key)) {
                System.out.println("[Celtium] CELTIUM_COPY_IMAGE glCopyImageSubData src=" + srcName
                        + " dst=" + dstName + " " + refusal + " requests=" + this.copyImageRequests
                        + " site=[" + celtium$callSite(8) + "]");
            }
            return;
        }
        // Snapshot the rectangle before filing it: the destination may be the source's own level, where GL
        // leaves an overlapping copy undefined - a snapshot at least makes it one whole older image rather
        // than a row-by-row smear.
        ByteBuffer patch = ByteBuffer.allocateDirect(width * height * 4);
        int rowBytes = width * 4;
        int sourceStride = source.width * 4;
        for (int row = 0; row < height; row++) {
            patch.put(row * rowBytes, source.data, (srcRecY + row) * sourceStride + srcRecX * 4, rowBytes);
        }
        // A patch the mirror has not consumed yet is a newer write to the source level than the record it
        // sits beside: where one overlaps the rectangle it wins, in the order the game wrote them.
        java.util.List<GlTextureCaptures.TexCapture> pending = this.texPatchBacklog.get(texLevelKey(srcName, srcLevel));
        if (pending != null) {
            for (GlTextureCaptures.TexCapture newer : pending) {
                int ix0 = Math.max(srcX, newer.x);
                int iy0 = Math.max(srcY, newer.y);
                int ix1 = Math.min(srcX + width, newer.x + newer.width);
                int iy1 = Math.min(srcY + height, newer.y + newer.height);
                if (ix0 >= ix1 || iy0 >= iy1) {
                    continue;
                }
                int span = (ix1 - ix0) * 4;
                for (int row = iy0; row < iy1; row++) {
                    patch.put((row - srcY) * rowBytes + (ix0 - srcX) * 4, newer.data,
                            (row - newer.y) * newer.width * 4 + (ix0 - newer.x) * 4, span);
                }
            }
        }
        fileTexturePatch(dstName, dstLevel, dstX, dstY, width, height, patch);
        this.copyImageServed++;
        if (this.copyImageServed == 1) {
            System.out.println("[Celtium] CELTIUM_COPY_IMAGE glCopyImageSubData src=" + srcName
                    + " level=" + srcLevel + " rect=" + srcX + "," + srcY + " " + width + "x" + height
                    + " -> dst=" + dstName + " level=" + dstLevel + " at=" + dstX + "," + dstY
                    + " servedFrom=captured-level requests=" + this.copyImageRequests
                    + " site=[" + celtium$callSite(8) + "]");
        }
    }

    /** GL_DEPTH_BUFFER_BIT. */
    private static final int DEPTH_BUFFER_BIT = 0x00000100;
    /** GL_NEAREST, the only filter a depth blit may name. */
    private static final int FILTER_NEAREST = 0x2600;

    private long blitDepthRequests;
    private static final java.util.Set<String> BLIT_DEPTH_SHAPES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * A {@code glBlitFramebuffer}, which was a table noop until the depth form needed a route: Iris's
     * pre-translucent shadow depth copy takes the blit form on the first frame after its targets are created
     * ({@code ShadowRenderTargets.copyPreTranslucentDepth}, the dirty-flag branch) and the texture-copy form
     * in steady state, so serving only the copy left the first frame's snapshot silently dropped. The depth
     * bit between two framebuffers that both record depth attachments routes to the backend's native depth
     * copy — equal extents only, where a copy and a NEAREST blit agree. Colour bits keep the historical noop:
     * no measured caller blits colour under GL-less, and the line below counts only what this handler decides.
     */
    private void celtium$noteBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1,
            int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if ((mask & DEPTH_BUFFER_BIT) == 0) {
            return;
        }
        this.blitDepthRequests++;
        int sourceTexture = GlsmFramebufferAttachments.current().depthTexture(this.readFbo);
        int destTexture = GlsmFramebufferAttachments.current().depthTexture(this.drawFbo);
        int width = srcX1 - srcX0;
        int height = srcY1 - srcY0;
        String servedFrom;
        if (sourceTexture == 0 || destTexture == 0) {
            servedFrom = "refused-depth-unrecorded: readFbo=" + this.readFbo + " drawFbo=" + this.drawFbo
                    + " sourceTexture=" + sourceTexture + " destTexture=" + destTexture;
        } else if (width <= 0 || height <= 0 || dstX1 - dstX0 != width || dstY1 - dstY0 != height
                || filter != FILTER_NEAREST) {
            servedFrom = "refused-depth-shape: src=" + width + "x" + height + " dst=" + (dstX1 - dstX0) + "x"
                    + (dstY1 - dstY0) + " filter=0x" + Integer.toHexString(filter)
                    + " (only an equal-extent NEAREST depth blit is a copy)";
        } else {
            try {
                Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                servedFrom = backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                        ? vulkan.celtium$serveDepthCopy(sourceTexture, destTexture, srcX0, srcY0, width, height,
                                dstX0, dstY0)
                        : "refused-no-vulkan-backend";
            } catch (RuntimeException | LinkageError error) {
                servedFrom = "refused-serve-failed: " + error;
            }
            if ("served".equals(servedFrom)) {
                servedFrom = "native-depth";
            }
        }
        String key = width + "x" + height + "/readFbo=" + this.readFbo + "/drawFbo=" + this.drawFbo
                + "/" + servedFrom;
        if (BLIT_DEPTH_SHAPES.size() < 8 && BLIT_DEPTH_SHAPES.add(key)) {
            System.out.println("[Celtium] CELTIUM_BLIT_DEPTH glBlitFramebuffer"
                    + " source=" + srcX0 + "," + srcY0 + " " + width + "x" + height
                    + " dest=" + dstX0 + "," + dstY0
                    + " readFbo=" + this.readFbo + " drawFbo=" + this.drawFbo
                    + " servedFrom=" + servedFrom + " requests=" + this.blitDepthRequests
                    + " site=[" + celtium$callSite(8) + "]");
        }
    }

    /**
     * A {@code glGetTexImage} of the bound texture, answered from that texture's mirror.
     * <p>
     * The game's screenshot reads this way rather than through {@code glReadPixels} whenever framebuffers are enabled,
     * which they are in a GL-less run - so before this route existed the buffer it encoded was the zero-writer's.
     */
    public static void celtium$getTexImage(int target, int level, int format, int type, long address) {
        CeltiumGlStub stub = installed;
        if (stub == null || target != 0x0DE1) {
            return;
        }
        long capacity = Long.MAX_VALUE;
        if (stub.pixelPackBuffer > 0) {
            // With a pack buffer bound the argument is an offset into it: resolve it against the buffer's storage,
            // and refuse an offset outside its logical size rather than write through a small integer or stale tail.
            ByteBuffer storage = GlsmBufferState.current().storage(stub.pixelPackBuffer);
            int logicalSize = storage == null ? 0
                    : stub.logicalSizeOf(stub.pixelPackBuffer, storage.capacity());
            if (storage == null || address < 0L || address >= logicalSize) {
                return;
            }
            capacity = logicalSize - address;
            address = memAddress(storage) + address;
        }
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            vulkan.celtium$getTexImage(stub.textures.texture2d(), level, format, type, address, capacity);
        }
    }

    /** The transformer's entry for {@code glReadPixels}: forwards to the installed stub when one exists. */
    public static void celtium$readPixels(int x, int y, int width, int height, int format, int type, long address) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.serveReadPixels(x, y, width, height, format, type, address);
        }
    }

    /**
     * Serves a {@code glReadPixels} against the image the bound read framebuffer stands for.
     * <p>
     * The resolution and the ordering live in the backend's copy service: the GL name finds the framebuffer's colour
     * texture (the raw {@code glFramebufferTexture2D} map, which vanilla attaches through too), the texture finds the
     * identity it was reported under, and the identity finds the pair its draws went into. When the renderer declines
     * - no backend, an image it does not own, a format it cannot pack - the buffer keeps whatever the caller put
     * there, which is the noop behaviour every previous caller saw and is at least no worse; the difference is the
     * call is now counted and the served case writes real pixels.
     */
    private void serveReadPixels(int x, int y, int width, int height, int format, int type, long address) {
        if (this.readPixelsEntries < 6) {
            this.readPixelsEntries++;
            System.out.println("[Celtium] CELTIUM_READ_PIXELS_ENTRY readFbo=" + this.readFbo
                    + " rect=" + x + "," + y + " " + width + "x" + height
                    + " format=0x" + Integer.toHexString(format) + " type=0x" + Integer.toHexString(type));
        }
        String pboRefusal = pixelBufferRefusal(this.pixelPackBuffer, address, "glReadPixels");
        if (pboRefusal != null) {
            if (this.pixelBufferRefusals < 8) {
                this.pixelBufferRefusals++;
                System.out.println("[Celtium] CELTIUM_PBO_REFUSED " + pboRefusal);
            }
            this.readPixelsCalls++;
            return;
        }
        try {
            Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            boolean served = backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                    && vulkan.celtium$readPixels(this.readFbo, x, y, width, height, format, type, address);
            if (!served && readPixelsRefusals < 8) {
                readPixelsRefusals++;
                System.out.println("[Celtium] CELTIUM_READ_PIXELS refused readFbo=" + this.readFbo
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

    /** Bounded refusal log + the call count for {@link #serveReadPixels}. */
    private int readPixelsRefusals;
    /** How many readback entries have been logged, so the path can be seen arriving. */
    private int readPixelsEntries;
    private long readPixelsCalls;

    private static void celtium$putInt(long address, int value) {
        MemoryUtil.memPutInt(address, value);
    }

    /** GL_ARRAY_BUFFER: the binding a client vertex pointer is an offset into (not a client address). */
    private static final int GL_ARRAY_BUFFER = 0x8892;
    /** The client-array capabilities: the vertex array is the one this path decodes, the others would add attributes. */
    private static final int GL_VERTEX_ARRAY = 0x8074;
    private static final int GL_NORMAL_ARRAY = 0x8075;
    private static final int GL_COLOR_ARRAY = 0x8076;
    private static final int GL_TEXTURE_COORD_ARRAY = 0x8078;
    /** GL_FLOAT, the only vertex-pointer element type this path decodes. */
    private static final int GL_FLOAT = 0x1406;
    /** GL_TEXTURE_2D, whose enable bit is reported beside each layout: an untextured pipeline needs it off. */
    static final int GL_TEXTURE_2D = 0x0DE1;
    /**
     * GL_FOG: the enable bit {@code RenderGlobal.renderSky} sets around the sky dome (RenderGlobal.java:1337-1345) and
     * around the horizon plane (:1478-1471) and clears before the stars (:1354). It is read at draw time, because the
     * immediate path's fog is per batch, not per frame.
     */
    static final int GL_FOG = 0x0B60;
    /** GL_QUADS: every {@code VertexBuffer.drawArrays} caller in the 1.12.2 sources passes 7. */
    private static final int GL_QUADS = 7;
    /**
     * The layout id this path offers the backend: three floats per vertex, no colour, no texture. It is
     * {@code VulkanImmediateFormats.POSITION} (7), the same id the uploader mixin gives
     * {@code DefaultVertexFormats.POSITION} - the format every one of those VertexBuffers was built with.
     */
    private static final int VERTEX_FORMAT_POSITION = 7;
    /** Bytes per vertex of that layout. */
    private static final int POSITION_STRIDE = 12;
    /** Guards the one-off "first draw" line, so a run records what it decoded once instead of per frame. */

    /**
     * One {@code glDrawArrays}, assembled from the mirrored array buffer and handed to the immediate path.
     * <p>
     * This exists because the sky, the stars and the horizon plane reach the screen through exactly this call and no
     * other. {@code RenderGlobal.renderSky} binds {@code skyVBO}/{@code starVBO}/{@code sky2VBO}, sets
     * {@code glVertexPointer(3, GL_FLOAT, 12, 0)} and calls {@code VertexBuffer.drawArrays(7)}, which is one line:
     * {@code GlStateManager.glDrawArrays(mode, 0, count)}. With no GL context that call used to end in this class's
     * generic no-op, so a GL-less frame had no sky at all while every counter reported a complete one.
     * <p>
     * What is decoded is deliberately one case, and it is the case those three draws are: a non-zero
     * {@code GL_ARRAY_BUFFER} binding whose bytes the stub mirrored, {@code GL_VERTEX_ARRAY} enabled, a
     * {@code GL_FLOAT} x3 pointer with stride 12 (three floats per vertex, position only) and mode 7
     * ({@code GL_QUADS}). Anything else is refused by name rather than submitted as a guess: the buffer bytes are
     * anonymous, and 28-byte {@code VboRenderList} chunk vertices decoded as 12-byte positions would draw terrain
     * from the wrong offsets - wrong pixels, with nothing in the frame to say so.
     * <p>
     * The colour does not travel in these vertices. Format 7 reads it from the backend's own colour mirror
     * ({@code VulkanTerrainGpuBackend.immediateState}), which is fed by {@code GlStateManagerMixin}'s
     * {@code GlStateManager.color} hook - the same value the font capture reads through
     * {@code CeltiumFontGlyphCapture.currentColor}. That is the colour {@code renderSky} sets immediately before each
     * of the three draws (the sky colour, the star brightness, then black for the horizon plane), so the sky arrives
     * tinted the way vanilla draws it without this class holding a second copy of the value.
     * <p>
     * The three callers in the 1.12.2 sources and whether this fixture reaches them (read from the sources, not
     * assumed): the dome {@code skyVBO} at {@code RenderGlobal.java:1340-1348} and the stars {@code starVBO} at
     * {@code :1437-1449} both run, the second only while {@code getStarBrightness() > 0} - the fixture pins
     * {@code worldTime} to 6000 from world tick 20 ({@code ValidationClient.java:2671}), where the celestial angle is 0
     * and {@code World.getStarBrightnessBody} clamps to exactly 0, so the captured frame has no star draw at all. The
     * horizon plane {@code sky2VBO} at {@code :1463-1480} is inside {@code if (d3 < 0.0D)}, where
     * {@code d3 = player.getPositionEyes(partialTicks).y - world.getHorizon()}, and the fixture's world is
     * {@code WorldType.FLAT} ({@code ValidationClient.java:2180}), whose {@code getHorizon} is 0.0
     * ({@code WorldType.java:186-189}) against an eye height of ~13.6 - so that branch is skipped and the horizon plane
     * is not drawn in either leg. The unconditional {@code glCallList(glSkyList2)} at {@code :1519} is
     * {@code glCallList(-1)} there, because {@code generateSky2} only assigns {@code glSkyList2} when {@code vboEnabled}
     * is false: a no-op in real GL and recorded as such here.
     */
    /** {@code glDrawElements} from the transformer's route: the one guarded indexed case this decoder can serve. */
    public static void celtium$drawElements(int mode, int count, int type, long indices) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.drawElementsNow(mode, count, type, indices, 0);
        }
    }

    /** {@code glMultiDrawArrays} from the transformer's route: each sub-draw is one decoded batch. */
    public static void celtium$multiDrawArrays(int mode, long firsts, long counts, int drawcount) {
        CeltiumGlStub stub = installed;
        if (stub == null || firsts == 0L || counts == 0L || drawcount <= 0) {
            return;
        }
        int n = Math.min(drawcount, 1024);
        for (int i = 0; i < n; i++) {
            int first = org.lwjgl.system.MemoryUtil.memGetInt(firsts + (long) i * 4L);
            int count = org.lwjgl.system.MemoryUtil.memGetInt(counts + (long) i * 4L);
            stub.drawArrays(mode, first, count);
        }
    }

    /** {@code glMultiDrawElements} from the transformer's route: an array of index pointers, each one batch. */
    public static void celtium$multiDrawElements(int mode, long counts, int type, long indices, int drawcount) {
        CeltiumGlStub stub = installed;
        if (stub == null || counts == 0L || indices == 0L || drawcount <= 0) {
            return;
        }
        int n = Math.min(drawcount, 1024);
        for (int i = 0; i < n; i++) {
            int count = org.lwjgl.system.MemoryUtil.memGetInt(counts + (long) i * 4L);
            long pointer = org.lwjgl.system.MemoryUtil.memGetLong(indices + (long) i * 8L);
            stub.drawElementsNow(mode, count, type, pointer, 0);
        }
    }

    /** {@code glDrawElementsBaseVertex}: the same decode, with the base vertex added to every index. */
    public static void celtium$drawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.drawElementsNow(mode, count, type, indices, baseVertex);
        }
    }

    /**
     * Indexed draws, decoded like {@link #drawArrays}: the layout comes from the vertex pointer's stride and the
     * enabled arrays - the legacy client enables OR the placements the bound VAO's attribute records prove
     * ({@link #celtium$vaoArrayMask(GlsmClientArrayState.VaoAttrib[], int)}) - and the indices from a mirrored element buffer.
     * Anything else is refused by name in the indexed census rather than guessed - the same rule the unindexed
     * decoder follows, and the reason a refused draw stays blank instead of drawing garbage.
     */
    private void drawElementsNow(int mode, int count, int type, long indices, int baseVertex) {
        if (count <= 0 || count > (1 << 20) || (mode != 4 && mode != 5 && mode != 7)) {
            return;
        }
        GlDrawCensus.DrawElementCensus.CALLS.incrementAndGet();
        int stride = VERTEX_POINTER.stride;
        if (VERTEX_POINTER.size != 3 || VERTEX_POINTER.type != GL_FLOAT
                || (stride != 12 && stride != 16 && stride != 20 && stride != 24 && stride != 32)) {
            GlDrawCensus.celtium$noteDrawElementRefusal("vertex pointer size=" + VERTEX_POINTER.size + " type=0x"
                    + Integer.toHexString(VERTEX_POINTER.type) + " stride=" + stride
                    + " is not a float3 layout this decode serves");
            GlDrawCensus.celtium$reportDrawElements();
            return;
        }
        NativeGeometryBuffer bound = NativeGeometryBuffer.bound();
        int boundName = NativeGeometryBuffer.boundId();
        java.nio.ByteBuffer vertices;
        try {
            vertices = bound != null ? bound.compatibilityView()
                    : boundName == 0 ? null : GlsmBufferState.current().storage(boundName);
        } catch (RuntimeException error) {
            return;
        }
        // A VAO-carried draw names its vertex buffer through attribute 0's record, not through the binding the
        // setup left behind - the resolution drawArrays already performs for the same reason (the fullscreen
        // quad's renderer unbinds its VBO before drawing). A VAO renderer's draw - Demonica's GLSM streaming
        // renderer is the measured one - reaches this decoder with the association recorded only there.
        GlsmClientArrayState.VaoAttrib[] attribs = boundAttribs();
        GlsmClientArrayState.VaoAttrib positionAttrib = attribs[0];
        if (positionAttrib.pointerSet && positionAttrib.buffer != 0 && positionAttrib.buffer != boundName) {
            bound = NativeGeometryBuffer.find(positionAttrib.buffer);
            boundName = positionAttrib.buffer;
            try {
                vertices = bound != null ? bound.compatibilityView() : GlsmBufferState.current().storage(boundName);
            } catch (RuntimeException error) {
                return;
            }
        }
        boolean clientVertices = boundName == 0;
        if (!clientVertices && vertices == null) {
            GlDrawCensus.celtium$noteDrawElementRefusal("the bound array buffer " + boundName + " has no mirrored storage");
            return;
        }
        long vertexBase;
        int vertexCapacity;
        if (clientVertices) {
            // No array buffer bound: the pointer glVertexPointer was given is a client address, the legacy spelling.
            vertexBase = VERTEX_POINTER.offset;
            if (vertexBase == 0L) {
                GlDrawCensus.celtium$noteDrawElementRefusal(
                        "the client vertex pointer is null, so there is no vertex array to read");
                return;
            }
            vertexCapacity = 0;
        } else {
            int logicalVertexSize = bound != null ? vertices.capacity()
                    : this.logicalSizeOf(boundName, vertices.capacity());
            long offset = VERTEX_POINTER.offset;
            if (offset < 0L || offset > logicalVertexSize) {
                GlDrawCensus.celtium$noteDrawElementRefusal("the vertex pointer offset " + offset + " is outside the "
                        + logicalVertexSize + "-byte buffer");
                return;
            }
            // A VBO pointer offset is part of the description, not a client address: the batch starts at it.
            vertexBase = memAddress(vertices, 0) + offset;
            vertexCapacity = logicalVertexSize - (int) offset;
        }
        // The same layout decision the unindexed decoder makes: the format id and the refusal reason. The mask
        // is the legacy client-array enables OR the layout the bound VAO's own attribute records prove - the
        // unindexed path reads those records through celtium$genericArrayMask, and a VAO renderer that never
        // touches the legacy enables (GLSM's streaming renderer) was refused here for layouts its records
        // describe exactly, which is the silence this census now names.
        String[] layoutRefusal = new String[1];
        int formatId;
        try {
            formatId = celtium$drawArrayFormat(stride,
                    celtium$otherArrayMask() | celtium$vaoArrayMask(attribs, stride), count, 0, clientVertices,
                    clientVertices ? 0L : vertexBase, vertexCapacity, layoutRefusal);
        } catch (RuntimeException error) {
            return;
        }
        if (layoutRefusal[0] != null) {
            GlDrawCensus.celtium$noteDrawElementRefusal(layoutRefusal[0]);
            GlDrawCensus.celtium$reportDrawElements();
            return;
        }
        int elementName = boundBuffer(0x8893); // GL_ELEMENT_ARRAY_BUFFER
        NativeGeometryBuffer elements = null;
        java.nio.ByteBuffer elementView = null;
        if (elementName > 0) {
            try {
                elements = NativeGeometryBuffer.find(elementName);
                elementView = elements != null ? elements.compatibilityView() : GlsmBufferState.current().storage(elementName);
            } catch (RuntimeException error) {
                return;
            }
            if (elementView == null) {
                GlDrawCensus.celtium$noteDrawElementRefusal(
                        "the bound element buffer " + elementName + " has no mirrored storage");
                return;
            }
        }
        int indexSize = type == 0x1401 ? 1 : type == 0x1403 ? 2 : type == 0x1405 ? 4 : 0;
        if (indexSize == 0 || indices < 0) {
            GlDrawCensus.celtium$noteDrawElementRefusal(
                    "index type 0x" + Integer.toHexString(type) + " is not a decodable index type");
            return;
        }
        long indexBytes = (long) count * indexSize;
        if (elementView != null) {
            int elementLogicalSize = elements != null ? elementView.capacity()
                    : this.logicalSizeOf(elementName, elementView.capacity());
            if (mapRangeOffset(indices, indexBytes, elementLogicalSize) < 0L) {
                GlDrawCensus.celtium$noteDrawElementRefusal("the draw reads offset " + indices + " + " + indexBytes
                        + " bytes of a " + elementLogicalSize + "-byte element buffer");
                return;
            }
        } else if (!celtium$readableMapping(indices, indexBytes)) {
            // No element buffer bound: the GL meaning of the pointer is client memory, read only when the range is
            // mapped - the same guard the client vertex path uses.
            GlDrawCensus.celtium$noteDrawElementRefusal("the client index pointer 0x" + Long.toHexString(indices) + " + "
                    + indexBytes + " bytes is not inside a readable mapping of this process");
            return;
        }
        int vertexCount = clientVertices ? Integer.MAX_VALUE : vertexCapacity / stride;
        if (stride == 32 && formatId != 8 && formatId != 9) {
            GlDrawCensus.celtium$noteDrawElementRefusal("stride 32 decoded as format " + formatId
                    + ", which the indexed repack does not carry");
            return;
        }
        java.nio.ByteBuffer elementReader = elementView == null ? null
                : elementView.duplicate().order(java.nio.ByteOrder.nativeOrder());
        java.nio.ByteBuffer expanded = org.lwjgl.system.MemoryUtil.memAlloc(count * stride);
        java.nio.ByteBuffer packed = null;
        try {
            int[] selected = new int[count];
            int maxIndex = -1;
            for (int i = 0; i < count; i++) {
                long at = indices + (long) i * indexSize;
                int index;
                if (elementReader != null) {
                    index = indexSize == 1 ? elementReader.get((int) at) & 0xFF
                            : indexSize == 2 ? elementReader.getShort((int) at) & 0xFFFF
                            : elementReader.getInt((int) at);
                } else {
                    index = indexSize == 1 ? org.lwjgl.system.MemoryUtil.memGetByte(at) & 0xFF
                            : indexSize == 2 ? org.lwjgl.system.MemoryUtil.memGetShort(at) & 0xFFFF
                            : org.lwjgl.system.MemoryUtil.memGetInt(at);
                }
                long resolvedIndex = (type == 0x1405 ? Integer.toUnsignedLong(index) : index) + (long) baseVertex;
                if (resolvedIndex < 0L || resolvedIndex >= vertexCount) {
                    GlDrawCensus.celtium$noteDrawElementRefusal(
                            "an index names vertex " + resolvedIndex + " of " + vertexCount);
                    return;
                }
                int resolved = (int) resolvedIndex;
                selected[i] = resolved;
                if (resolved > maxIndex) {
                    maxIndex = resolved;
                }
            }
            if (clientVertices && maxIndex >= 0
                    && !celtium$readableMapping(vertexBase, (long) (maxIndex + 1) * stride)) {
                GlDrawCensus.celtium$noteDrawElementRefusal("the client vertex range 0x" + Long.toHexString(vertexBase)
                        + " + " + (long) (maxIndex + 1) * stride
                        + " bytes is not inside a readable mapping of this process");
                return;
            }
            for (int i = 0; i < count; i++) {
                org.lwjgl.system.MemoryUtil.memCopy(vertexBase + (long) selected[i] * stride,
                        org.lwjgl.system.MemoryUtil.memAddress(expanded) + (long) i * stride, stride);
            }
            // The 32-byte forms carry a padded attribute the reference decoder repacks (to 28 or 24 bytes); the
            // same helper runs on the expanded batch so the submit stride and layout match the format id.
            int submitStride = stride;
            if (stride == 32) {
                packed = celtium$copyClientVerticesForDraw(org.lwjgl.system.MemoryUtil.memAddress(expanded), count,
                        stride, formatId);
                submitStride = formatId == 9 ? 24 : 28;
            }
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend != null) {
                backend.submitImmediateBatch(null, submitStride, formatId,
                        org.lwjgl.system.MemoryUtil.memAddress(packed != null ? packed : expanded), count, mode);
                GlDrawCensus.DrawElementCensus.DRAWN.incrementAndGet();
                GlDrawCensus.celtium$reportDrawElements();
            } else {
                GlDrawCensus.celtium$noteDrawElementRefusal("no terrain backend is current, so the batch has nowhere to go");
            }
        } catch (RuntimeException | LinkageError error) {
            // A malformed batch draws nothing; a half-built one must not reach the backend either.
            GlDrawCensus.celtium$noteDrawElementRefusal("the decode threw " + error.getClass().getSimpleName());
        } finally {
            if (packed != null) {
                org.lwjgl.system.MemoryUtil.memFree(packed);
            }
            org.lwjgl.system.MemoryUtil.memFree(expanded);
        }
    }

    private void drawArrays(int mode, int first, int count) { drawArrays(mode, first, count, false); }

    private void drawArrays(int mode, int first, int count, boolean compile) {
        GlDrawCensus.DRAWARRAY_CALLS.incrementAndGet();
        // The owned-buffer publication is consumed by every draw, matched or not: one publication belongs to the one
        // glDrawArrays that follows it, and a publication left behind would otherwise be read as the next draw's
        // ownership claim - which is the mistake this entry point exists to remove.
        long[] owned = celtium$consumeOwnedClientBuffer();
        if (count <= 0) {
            // GL draws nothing for an empty range, and so does this: counting it as a gap would be a lie.
            GlDrawCensus.DRAWARRAY_EMPTY.incrementAndGet();
            celtium$reportDrawArrays();
            return;
        }
        // A mod can bind through Minecraft's VertexBuffer API and issue the draw through raw GL. Only that raw call
        // enters this decoder; the native API draw uses the retained mesh directly and never mirrors a GL buffer.
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.bound();
        int boundName = NativeGeometryBuffer.boundId();
        int buffer = boundName;
        ByteBuffer storage = nativeBuffer != null ? nativeBuffer.compatibilityView()
                : buffer == 0 ? null : GlsmBufferState.current().storage(buffer);
        // A VAO-carried draw names its vertex buffer through attribute 0's record, not through the binding the
        // setup left behind: the full-screen quad's renderer binds its VBO only while it sets the pointers and
        // unbinds it again before the draw, so the live binding at draw time is 0 and the pointer read as client
        // memory. The record is the only place the association survives (offer-path diagnosis, 2026-11-20).
        GlsmClientArrayState.VaoAttrib[] attribs = boundAttribs();
        GlsmClientArrayState.VaoAttrib position = attribs[0];
        if (position.pointerSet && position.buffer != 0 && position.buffer != boundName) {
            nativeBuffer = NativeGeometryBuffer.find(position.buffer);
            buffer = position.buffer;
            storage = nativeBuffer != null ? nativeBuffer.compatibilityView() : GlsmBufferState.current().storage(buffer);
        }
        // No array buffer bound means the pointer glVertexPointer was handed is a **client address**, not an offset
        // into GL-owned storage: the vertices are process memory the game wrote, and under a stub there is no GL object
        // to mirror them from. That is the main menu, the loading screen and every GUI-only frame - measured in the
        // user's own launch as a million refusals of exactly this shape while the swapchain presented 2700 frames,
        // i.e. a window with nothing in it. The read is the same read a driver performs for the same call; what this
        // class adds is the guard below, because a stale pointer would fault the process and a blank menu is the
        // lesser failure.
        boolean clientMemory = buffer == 0;
        // The split-array repack reads client pointers, so the layout may only be accepted when the vertices are client
        // memory: a VBO-bound draw carries offsets, and repacking those addresses is a segfault (measured: SIGSEGV in
        // repackPaddedTexColor on the first run where the projection guard stopped refusing these draws).
        this.drawClientMemory = clientMemory;
        this.texColorRepackArmed = false;
        long offset = VERTEX_POINTER.offset;
        // A stride of 0 is GL's tightly-packed spelling: the attribute's own size in floats, not a stride of zero.
        int pointerStride = VERTEX_POINTER.stride != 0 ? VERTEX_POINTER.stride
                : VERTEX_POINTER.type == GL_FLOAT ? VERTEX_POINTER.size * 4 : VERTEX_POINTER.stride;
        long end = 0L;
        long asked = 0L;
        int formatId = 0;
        String reason = null;
        if (storage == null && !clientMemory) {
            reason = "the bound array buffer " + buffer + " has no mirrored storage";
        } else if (!GlsmEnableState.current().enabled(GL_VERTEX_ARRAY) && !GlsmEnableState.current().enabled(0x8622) && !position.enabled) {
            // Core code enables attribute 0 instead of the legacy GL_VERTEX_ARRAY bit; both describe the same position
            // attribute the decode reads, so either enable makes this draw describable. The third spelling is the
            // VAO's own per-attribute enable, which is the only one a VAO setup leaves behind - the two global bits
            // hold whatever the last client-state caller set, and reading them for a VAO draw refused the
            // full-screen quad on every frame.
            reason = "GL_VERTEX_ARRAY / attribute 0 is not enabled, so no vertex pointer describes this draw";
        } else if (mode != GL_QUADS && mode != 4 && mode != 6 && mode != 5 && mode != 8 && mode != 9
                && mode != 1 && mode != 2 && mode != 3) {
            reason = "draw mode " + mode + " is not handled (quads/triangles/fans/strips/polygons/lines only)";
        } else if (mode == 6 && count < 3) {
            // A fan with fewer than three vertices has no triangles to present.
            GlDrawCensus.DRAWARRAY_EMPTY.incrementAndGet();
            celtium$reportDrawArrays();
            return;
        } else if (VERTEX_POINTER.size != 3 || VERTEX_POINTER.type != GL_FLOAT) {
            reason = "vertex pointer size=" + VERTEX_POINTER.size + " type=0x"
                    + Integer.toHexString(VERTEX_POINTER.type)
                    + " is not the float3 position every layout this path decodes uses";
        } else {
            String[] layoutRefusal = new String[1];
            long storageBase = (clientMemory || storage == null) ? 0L : memAddress(storage, 0);
            int storageCapacity = (clientMemory || storage == null) ? 0
                    : (nativeBuffer != null ? storage.capacity() : this.logicalSizeOf(buffer, storage.capacity()));
            formatId = celtium$drawArrayFormat(pointerStride,
                    celtium$otherArrayMask() | celtium$genericArrayMask(attribs), count, first,
                    clientMemory, storageBase, storageCapacity, layoutRefusal);
            reason = layoutRefusal[0];
            if (reason == null && clientMemory && offset == 0L) {
                reason = "the client vertex pointer is null, so there is no vertex array to read";
            }
        }
        if (reason == null && (first < 0 || offset < 0L)) {
            reason = "first=" + first + " and pointer offset=" + offset + " must not be negative";
        }
        if (reason == null) {
            asked = (long) first * pointerStride + (long) count * pointerStride;
            if (clientMemory) {
                // Two bounds are available for a client pointer, and they are not equal in strength.
                //
                // The owned-buffer publication is the strong one (B09): the game's own {@code BufferBuilder} buffer,
                // named with its address and its capacity by the uploader mixin immediately before this draw, so the
                // whole range is inside a buffer the process demonstrably owns and the length is known rather than
                // guessed. The /proc mapping list is the weak one: it says a range is readable now, which is a guard
                // against faulting the JVM, not a statement about who owns the memory or how long it lives - and it is
                // Linux-only. A draw the publication covers needs no guard at all; a draw it does not cover (a mod's
                // direct client-array call, or a pointer the publication does not describe) keeps the guard and the
                // cap, and is counted separately so the two paths stay distinguishable.
                boolean ownedCovers = owned != null
                        && ClientArrayRange.covers(owned[0], owned[1], offset, asked);
                if (asked > MAX_CLIENT_DRAW_BYTES) {
                    reason = "the draw asks for " + asked + " bytes of client memory, over the "
                            + MAX_CLIENT_DRAW_BYTES + "-byte cap one draw may read";
                } else if (ownedCovers) {
                    ownedClientBufferDecoded++;
                    clientPointerAccepted++;
                } else if (!celtium$readableMapping(offset, asked)) {
                    if (owned != null) {
                        // The uploader published a buffer and it does not cover this draw: the two entries are about
                        // different memory, which is a decode gap worth naming rather than silently guarding.
                        ownedClientBufferMismatch++;
                    }
                    reason = "the client pointer 0x" + Long.toHexString(offset) + " + " + asked
                            + " bytes is not inside a readable mapping of this process"
                            + (owned == null ? " and no owned buffer was published for it"
                                    : " and the published owned buffer 0x" + Long.toHexString(owned[0]) + "+"
                                            + owned[1] + " does not cover it");
                } else {
                    pointerOnlyDecoded++;
                }
            } else {
                int logicalSize = nativeBuffer != null ? storage.capacity()
                        : this.logicalSizeOf(buffer, storage.capacity());
                if (mapRangeOffset(offset, asked, logicalSize) < 0L) {
                    reason = "the draw reads offset " + offset + " + " + asked + " bytes of a "
                            + logicalSize + "-byte buffer";
                }
            }
        }
        if (reason == null) {
            celtium$noteClientTuple(clientMemory, buffer, count, formatId);
        }
        if (GlsmShaderLedger.current().currentProgram() != 0
                && this.celtium$programDraws.add(GlsmShaderLedger.current().currentProgram())) {
            System.out.println("[Celtium] CELTIUM_PROGRAM_DRAW program=" + GlsmShaderLedger.current().currentProgram() + " mode=" + mode
                    + " count=" + count + " format=" + formatId + " reason=" + reason);
        }
        long address = 0L;
        if (reason == null) {
            int stride = pointerStride;
            address = (clientMemory ? offset : memAddress(storage, 0) + offset) + (long) first * stride;
            int decodedStride = formatId == FORMAT_POSITION_TEX_COLOR && this.texColorRepackArmed ? 24
                    : pointerStride == 32 && formatId == 9 ? 24
                    : pointerStride == 32 && formatId == 8 ? 28 : pointerStride;
            if (compile) {
                long bytes = (long) count * decodedStride;
                if (bytes > MAX_LIST_PAYLOAD_BYTES) {
                    this.listPayloadsUnsized++;
                    GlDrawCensus.DRAWARRAY_REFUSED.incrementAndGet();
                    return;
                }
                ByteBuffer snapshot = celtium$copyClientVerticesForDraw(address, count, pointerStride, formatId);
                NativeGeometryBuffer geometry = new NativeGeometryBuffer();
                try {
                    geometry.upload(snapshot, decodedStride, formatId);
                } catch (RuntimeException | Error error) {
                    geometry.close();
                    throw error;
                } finally { MemoryUtil.memFree(snapshot); }
                if (this.openListGeometry == null) this.openListGeometry = new ArrayList<NativeGeometryBuffer>();
                this.openListGeometry.add(geometry);
                long litId = 0L;
                if (hasItemTwin(pointerStride, formatId)) {
                    ByteBuffer litSnapshot = copyClientVerticesAsItem(address, count, formatId);
                    NativeGeometryBuffer lit = new NativeGeometryBuffer();
                    try {
                        lit.upload(litSnapshot, ITEM_STRIDE, ITEM_FORMAT);
                    } catch (RuntimeException | Error error) {
                        lit.close();
                        throw error;
                    } finally { MemoryUtil.memFree(litSnapshot); }
                    this.openListGeometry.add(lit);
                    litId = lit.id();
                }
                this.openList.add(new Object[] {COMPILED_ARRAYS,
                        new long[] {geometry.id(), decodedStride, formatId, count, mode, litId}});
                this.listPayloadsOwned++;
                return;
            }
            TerrainGpuBackend backend;
            try {
                // Resolved here rather than held as a field: this class is installed when the window is created, the
                // backend only when the renderer comes up, and a field captured early would be permanently null.
                backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            } catch (RuntimeException | LinkageError error) {
                backend = null;
                reason = "the terrain backend could not be reached: " + error;
            }
            if (backend == null && reason == null) {
                reason = "no terrain backend is installed";
            }
            if (backend != null && reason == null) {
                reason = celtium$projectionRefusal(backend, this.drawFbo);
            }
            if (backend != null && reason == null) {
                // The two conditions that used to refuse a draw here are gone, each because the capability it named
                // now exists - and each removal was measured rather than assumed:
                //   the fog: the immediate path applies the fixed-function fog (u_FogColor/u_FogParams at 128..207),
                //     and a leg on the jar that carries it shows the dome's band as a gradient identical to upstream
                //     Celeritas' (rows 0-199: max channel difference 4, no pixel over 8, row means equal to a tenth);
                //   the alpha: the position shaders read the colour's alpha from push-constant offset 96, so a
                //     translucent batch is drawn in its own alpha instead of opaque (this class refused the star draw
                //     until that slot existed).
                // What is left is celtium$projectionRefusal above, which guards a transform rather than a capability,
                // and the decode's own refusals. A draw that leaves here is one the immediate path can reproduce.
                celtium$noteFogDegradation(backend, buffer, count);
            }
            if (backend != null && reason == null) {
                boolean taken;
                try {
                    // null vertexFormat: the backend keys this batch on the format id and never reads the object.
                    // A refused batch is drawn by nobody under the GL-less window, which is why the reason is kept.
                    // The stride and the format are the decoded batch's own now, not the position-only pair: the
                    // client-memory path the menu and the loading screen use is stride 16 (POSITION_COLOR, id 1), and
                    // Forge's cloud VBO is stride 24 (POSITION_TEX_COLOR, id 2) - both carry attributes the format-7
                    // shader would read as the next vertex's position.
                    boolean asItem = hasItemTwin(pointerStride, formatId) && lightingOn(backend);
                    ByteBuffer packed = asItem ? copyClientVerticesAsItem(address, count, formatId)
                            : decodedStride == pointerStride ? null
                            : celtium$copyClientVerticesForDraw(address, count, pointerStride, formatId);
                    try {
                        taken = backend.submitImmediateBatch(null, asItem ? ITEM_STRIDE : decodedStride,
                                asItem ? ITEM_FORMAT : formatId,
                                packed == null ? address : memAddress(packed), count, mode);
                    } finally {
                        if (packed != null) MemoryUtil.memFree(packed);
                    }
                } catch (RuntimeException | LinkageError error) {
                    taken = false;
                    reason = "the backend threw: " + error;
                }
                if (taken) {
                    GlDrawCensus.DRAWARRAY_DRAWN.incrementAndGet();
                    GlDrawCensus.DRAWARRAY_VERTICES.addAndGet(count);
                    celtium$noteAcceptedColor(backend, buffer, count);
                    celtium$noteParticleTexCoords(formatId, address, count);
                    if (celtium$noteDrawArrayLayout(buffer) || DRAWARRAY_PROBE_LINES.getAndDecrement() > 0) {
                        // One line per distinct buffer, at its first accepted draw: what the batch contains, what the
                        // pointer state said it was, and the matrices the immediate path will draw it with. An accepted
                        // draw whose transform is wrong is a wrong picture, and the two halves have to be told apart
                        // from the outside - "the vertices are not the sky" and "the sky is drawn with the identity"
                        // look the same in the frame and are different findings.
                        StringBuilder vertices = new StringBuilder();
                        for (int i = 0; i < 4 && i < count; i++) {
                            long at = address + (long) i * stride;
                            vertices.append(i == 0 ? "" : " ").append('(')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at))).append(',')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at + 4))).append(',')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at + 8))).append(')');
                        }
                        // The UVs beside the positions, because a plane that samples one texel everywhere looks exactly
                        // like a plane whose texture is faint: measuring the cloud batch's decoded UVs is what separates
                        // "the decode reads the right attribute" from "every fragment samples the same texel". Read at
                        // the same offsets the layout table gives (POSITION_TEX_COLOR: pos 0..11, uv 12..19, colour 20..23).
                        StringBuilder uvs = new StringBuilder();
                        for (int i = 0; i < 4 && i < count; i++) {
                            long at = address + (long) i * stride;
                            uvs.append(i == 0 ? "" : " ").append('(')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at + 12))).append(',')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at + 16))).append(')');
                        }
                        // The colour bytes beside the UVs, from the same four vertices. The cloud batch's layout line
                        // already reports stride 24 (POSITION_TEX_COLOR) and otherArrays=GL_COLOR_ARRAY, so the shape
                        // is right; what it could not say is what the colour array holds. Vanilla draws the clouds
                        // with color(f, f1, f2, 0.8F) and the fragment multiplies texture x v_Color, so a colour of
                        // alpha 0xCC here with faint clouds in the frame means the loss is downstream, and a colour of
                        // alpha 0x00 means the array is not being read at all. Measured 2026-10-16: the white-poison
                        // arm (texture 35 forced to opaque white) is pixel-identical to the magenta-poison arm, which
                        // is why the question is now the colour and the blend rather than the texture.
                        StringBuilder colours = new StringBuilder();
                        int colourOffset = stride >= 24 ? 20 : 16;
                        for (int i = 0; i < 4 && i < count; i++) {
                            long at = address + (long) i * stride + colourOffset;
                            colours.append(i == 0 ? "" : " ").append(String.format("%02x%02x%02x%02x",
                                    MemoryUtil.memGetByte(at + 0), MemoryUtil.memGetByte(at + 1),
                                    MemoryUtil.memGetByte(at + 2), MemoryUtil.memGetByte(at + 3)));
                        }
                        // The last vertex's UV beside the first four. The cloud batch's first quad spans 0.016 in u and
                        // v, which is exactly the 32-block cell vanilla advances, but four vertices cannot say whether
                        // the whole batch tiles the texture or samples one cell everywhere - and a plane that samples
                        // one 4x4-texel patch is a plane that is 80% "gap" by the texture's own alpha histogram
                        // (52,180 of 65,536 texels are alpha 1). This is the number that separates those two.
                        StringBuilder tailUv = new StringBuilder("none");
                        if (count > 4) {
                            long at = address + (long) (count - 1) * stride;
                            tailUv.setLength(0);
                            tailUv.append('(').append(celtium$round3(MemoryUtil.memGetFloat(at + 12))).append(',')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at + 16))).append(')');
                        }
                        System.out.println("[Celtium] CELTIUM_DRAWARRAY_LAYOUT first=" + first + " count=" + count
                                + " uvLast=" + tailUv
                                + " stride=" + stride + " format=" + formatId + " mode=" + mode
                                + " col0..col3=[" + colours + "]"
                                + " uv0..uv3=[" + uvs + "]"
                                + " buffer=" + buffer + " clientMemory=" + clientMemory + " pointerOffset=" + offset
                                + " pointerSize=" + VERTEX_POINTER.size + " pointerType=0x"
                                + Integer.toHexString(VERTEX_POINTER.type) + " pointerStride="
                                + stride + " otherArrays=" + celtium$otherArrayNames()
                                + " v0..v3=[" + vertices + "] "
                                + celtium$mirroredMatrices(backend)
                                + " texture2dEnabled=" + GlsmEnableState.current().enabled(GL_TEXTURE_2D)
                                + " site=[" + celtium$callSite(10) + "]");
                        GlDrawCensus.drawArrayFirst = "mode=" + mode + " first=" + first + " count=" + count + " stride="
                                + stride + " format=" + formatId + " buffer=" + buffer
                                + " pointerOffset=" + offset + " address=0x" + Long.toHexString(address);
                    }
                    celtium$reportDrawArrays();
                    return;
                }
                if (reason == null) {
                    String refusal = backend.lastRefusal();
                    reason = "the backend refused it: "
                            + (refusal == null || refusal.isEmpty() ? "no reason recorded" : refusal);
                }
            }
        }
        celtium$noteDrawArrayRefusalContext(reason);
        GlDrawCensus.celtium$noteDrawArrayRefusal(reason);
        celtium$reportDrawArrays();
    }

    /**
     * One line per distinct refusal *context*, bounded, so a refused draw can be attributed to a drawable.
     * <p>
     * The reason alone does not say which surface the draw was meant for, and the two cases need different fixes: a
     * batch drawn into a 256x256 framebuffer of its own (Forge's splash screen binds one) belongs to nobody here and
     * must stay refused, while a batch drawn into the window with a 256x256 viewport is a legal GL draw whose viewport
     * this renderer should apply instead of rejecting. The bound draw framebuffer is the stub's own record of
     * glBindFramebuffer, and the viewport is its record of glViewport, so the line reports the state the call really
     * saw rather than a guess from the size.
     */
    private static final java.util.Set<String> REFUSAL_CONTEXTS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void celtium$noteDrawArrayRefusalContext(String reason) {
        if (REFUSAL_CONTEXTS.size() >= 8) {
            return;
        }
        String shortReason = reason == null ? "none"
                : reason.substring(0, Math.min(48, reason.length()));
        String key = this.drawFbo + "/" + GlsmViewportState.current().width() + "x" + GlsmViewportState.current().height() + "/" + shortReason;
        if (!REFUSAL_CONTEXTS.add(key)) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_DRAWARRAY_REFUSAL_CONTEXT drawFbo=" + this.drawFbo
                + " readFbo=" + this.readFbo
                + " viewport=" + GlsmViewportState.current().width() + "x" + GlsmViewportState.current().height()
                + " stride=" + VERTEX_POINTER.stride
                + " otherArrays=" + celtium$otherArrayNames()
                + " reason=" + shortReason
                + " site=[" + celtium$callSite(8) + "]");
    }

    /**
     * {@return the enabled client arrays other than the vertex array, or {@code null} when only the vertex array is on}.
     * <p>
     * A 12-byte stride describes a vertex only if the vertex array is the only one in play. GL is asked through the
     * same enable record {@code glEnableClientState} writes, so this is the state the draw would really have seen.
     */
    private String celtium$otherClientArrays() {
        StringBuilder enabled = new StringBuilder();
        int[] caps = { GL_NORMAL_ARRAY, GL_COLOR_ARRAY, GL_TEXTURE_COORD_ARRAY };
        String[] names = { "GL_NORMAL_ARRAY", "GL_COLOR_ARRAY", "GL_TEXTURE_COORD_ARRAY" };
        for (int i = 0; i < caps.length; i++) {
            if (GlsmEnableState.current().enabled(caps[i])) {
                if (enabled.length() > 0) {
                    enabled.append(", ");
                }
                enabled.append(names[i]);
            }
        }
        return enabled.length() == 0 ? null : enabled.toString();
    }

    /**
     * {@return the enabled client arrays besides the vertex array, as a bit mask}: bit 0 normal, bit 1 colour, bit 2
     * texture coordinates. The layout table below keys on it, because a stride alone does not name a format: 16 bytes
     * is POSITION_COLOR with the colour array on and POSITION_NORMAL with the normal array on, and 24 is
     * POSITION_TEX_COLOR with colour and 24 is POSITION_TEX_NORMAL with a normal.
     */
    private int celtium$otherArrayMask() {
        int mask = 0;
        if (GlsmEnableState.current().enabled(GL_NORMAL_ARRAY)) {
            mask |= 1;
        }
        if (GlsmEnableState.current().enabled(GL_COLOR_ARRAY)) {
            mask |= 2;
        }
        if (GlsmEnableState.current().enabled(GL_TEXTURE_COORD_ARRAY)) {
            mask |= 4;
        }
        return mask;
    }

    /**
     * The layout mask a VAO's generic attributes add, in {@link #celtium$otherArrayMask()}'s numbering: an enabled
     * float attribute past position is a texture-coordinate set for layout purposes. The full-screen quad carries
     * two of them (both reading the same bytes at offset 12), and the layout table's stride-20 row is the
     * position-plus-texture form - without this bit the tuple read as "no other array" and the row never matched.
     * Only float attributes count: any other type's bytes are not the floats the layouts describe.
     */
    private static int celtium$genericArrayMask(GlsmClientArrayState.VaoAttrib[] attribs) {
        for (int i = 1; i < attribs.length; i++) {
            GlsmClientArrayState.VaoAttrib attrib = attribs[i];
            if (attrib.enabled && attrib.pointerSet && attrib.type == GL_FLOAT) {
                return 4;
            }
        }
        return 0;
    }

    /**
     * The layout mask a VAO's generic attributes prove for the indexed decoder, in
     * {@link #celtium$otherArrayMask()}'s numbering. {@link #celtium$genericArrayMask(GlsmClientArrayState.VaoAttrib[])} can only
     * name the texture bit - it accepts any enabled float attribute past position - which leaves a VAO
     * renderer whose colour rides in a normalized byte attribute (Demonica's GLSM streaming renderer is the
     * measured one) undecodable even though its records describe the layout exactly. Here a bit is granted
     * only when a record confirms the field's canonical placement for a row the layout table knows - type,
     * size, normalization and offset all have to match that row - so a VAO interleaved differently keeps
     * the refusal instead of being decoded as a layout it is not.
     */
    static int celtium$vaoArrayMask(GlsmClientArrayState.VaoAttrib[] attribs, int stride) {
        int mask = 0;
        for (int i = 1; i < attribs.length; i++) {
            GlsmClientArrayState.VaoAttrib attrib = attribs[i];
            if (!attrib.enabled || !attrib.pointerSet) {
                continue;
            }
            if (attrib.type == GL_FLOAT && attrib.size == 2 && attrib.offset == 12L) {
                // The texture coordinates: at byte 12 in every interleaved layout the table knows.
                mask |= 4;
            } else if (attrib.type == GL_UNSIGNED_BYTE && attrib.size == 4 && attrib.normalized
                    && attrib.offset == (stride == 16 ? 12L : 20L)) {
                // The vertex colour: after the position alone at stride 16, after position+texture above it.
                mask |= 2;
            } else if (attrib.type == GL_BYTE_VALUE && attrib.size == 3 && attrib.normalized
                    && attrib.offset == (stride == 16 ? 12L : stride == 24 ? 20L : 24L)) {
                // The byte normal: after the position at stride 16, after position+texture at 24, and after
                // position+texture+colour at 32.
                mask |= 1;
            }
        }
        return mask;
    }

    /** The same mask as text, for the refusal and layout lines. */
    private String celtium$otherArrayNames() {
        String text = celtium$otherClientArrays();
        return text == null ? "none" : text;
    }

    /** How many bytes of client memory one draw may read: a bound on a pointer whose true length is unknown. */
    private static final long MAX_CLIENT_DRAW_BYTES = 32L * 1024L * 1024L;

    /**
     * The layouts this decoder hands to the immediate path, chosen by the vertex pointer's stride and the client
     * arrays that are enabled, with the format id the immediate path is asked for.
     * <p>
     * Three tuples are measured, and each one is a real call site rather than a guess:
     * <ul>
     * <li>{@code stride 12, no other array} - the sky dome and the horizon plane, {@code VertexBuffer.drawArrays(7)}
     * after {@code glVertexPointer(3, GL_FLOAT, 12, 0)}. This is the case the class decoded before.</li>
     * <li>{@code stride 24} with colour and texture - Forge's cloud renderer. {@code FMLClientHandler.renderClouds}
     * calls {@code CloudRenderer.render}, which draws {@code vbo.func_177358_a(GL_QUADS)} from a 24-byte
     * POSITION_TEX_COLOR vertex, so the clouds stopped reaching vanilla's fancy path after the VBO was built and this
     * draw was refused for its stride (52 refusals at about one per frame).</li>
     * <li>{@code stride 16} with colour - the main menu and the loading screen, whose client-memory pointers are
     * float3 with stride 16 at real process addresses (four distinct tuples in the user's own launch log).</li>
     * </ul>
     * Anything else is refused by name with the tuple and the enabled arrays on the line, because a wrong layout is a
     * wrong picture with nothing in the frame to say so - the same rule the position-only case has always followed.
     */
    private int celtium$drawArrayFormat(int stride, int otherArrays, int count, int first, boolean clientMemory,
            long storageBase, int storageCapacity, String[] refusalOut) {
        int id = 0;
        String what = null;
        if (stride == POSITION_STRIDE && otherArrays == 0) {
            id = VERTEX_FORMAT_POSITION;             // 7: position, colour from the mirror
        } else if (stride == 16 && otherArrays == 2) {
            id = FORMAT_POSITION_COLOR;              // 1: position and a 4-byte colour in the vertex
        } else if (stride == 16 && otherArrays == 1) {
            id = FORMAT_POSITION_NORMAL;             // 10: position and a normal
        } else if (stride == 20 && otherArrays == 4) {
            id = FORMAT_POSITION_TEX;                // 6: position and texture
        } else if (stride == 24 && otherArrays == 6) {
            id = FORMAT_POSITION_TEX_COLOR;          // 2: position, texture and colour - Forge's clouds
        } else if (stride == 32 && otherArrays == 5) {
            id = FORMAT_POSITION_TEX_NORMAL; // legacy interleaved UV12, byte normal24; repacked before submission
        } else if (stride == 32 && otherArrays == 7) {
            id = 8; // legacy interleaved UV12, colour20, byte normal24; omit trailing padding
        } else if (otherArrays == 7
                && celtium$splitArraysValid(stride, count, first, clientMemory, storageBase, storageCapacity,
                        refusalOut)) {
            // Immersive Railroading's item models: position, texcoord, float4 colour and a normal in one 48-byte
            // vertex, drawn from separate client pointers. The normal is not drawn and the three arrays this decoder
            // can read are repacked into POSITION_TEX_COLOR; the refusal this replaces fired eight times per run.
            id = FORMAT_POSITION_TEX_COLOR;
        } else if (stride == 24 && otherArrays == 5) {
            id = FORMAT_POSITION_TEX_NORMAL;         // 9: position, texture and a normal
        } else {
            what = "stride " + stride + " with client arrays [" + celtium$otherArrayNames()
                    + "] is not a layout this decode knows";
        }
        if (what != null) {
            refusalOut[0] = what;
            return 0;
        }
        refusalOut[0] = null;
        return id;
    }

    /** The immediate path's format ids, named here so the table above reads as a table. */
    private static final int FORMAT_POSITION_COLOR = 1;
    private static final int FORMAT_POSITION_TEX_COLOR = 2;
    private static final int FORMAT_POSITION_TEX = 6;
    private static final int FORMAT_POSITION_TEX_NORMAL = 9;
    private static final int FORMAT_POSITION_NORMAL = 10;
    /** PARTICLE_POSITION_TEX_COLOR_LMAP: 3f position, 2f tex, 4ub colour, 2s lightmap - the tex coords at byte 12. */
    private static final int FORMAT_PARTICLE = 5;

    /**
     * {@return whether {@code [address, address + bytes)} lies inside readable mapping(s) of this process}.
     * <p>
     * This is the weak entry point, used only when the owned-buffer publication does not describe the draw. A client
     * pointer carries no length and no owner: it is a process address the game wrote, and dereferencing one that is
     * stale, freed or never valid faults the JVM, which is a worse outcome than the blank menu this task exists to
     * fix. So the mapping list the kernel already keeps is read once and cached, and a draw is only decoded when the
     * whole range it asks for is covered by committed readable mapping(s) with no unmapped gap. A miss refreshes the
     * list once, because a buffer allocated since the last read is a legitimate miss; a second miss for the same
     * address is a refusal, and the refresh rate is bounded so a run cannot turn a bad pointer into a parsing loop.
     * <p>
     * What this is <em>not</em>: an ownership or lifetime record. A hit says the range is readable at this moment, not
     * that it is still the game's buffer or that it will stay mapped; the cached list can also go stale between
     * refreshes, and the refresh budget (64) is a bound on this guard rather than a property of the memory. The
     * authoritative statement about a client-array draw is the {@link #publishClientArrayBuffer owned-buffer
     * publication}; this function exists for the calls that do not come through Minecraft's uploader.
     * <p>
     * Supported on Linux ({@code /proc/self/maps}), Windows ({@code VirtualQuery} via FFM) and macOS
     * ({@code mach_vm_region} via FFM); on other systems where
     * no mapping provider is available this returns false, so a pointer-only client-memory draw stays a named refusal
     * rather than becoming an unguarded read - and on such a system the owned-buffer path still decodes the game's
     * own batches.
     */
    private boolean celtium$readableMapping(long address, long bytes) {
        if (address <= 0L || bytes <= 0L) {
            return false;
        }
        long end;
        try {
            end = Math.addExact(address, bytes);
        } catch (ArithmeticException overflow) {
            return false;
        }
        ReadableMemoryRanges.Range[] ranges = this.readableRanges;
        if (ranges == null || !ReadableMemoryRanges.containsBounds(ranges, address, end)) {
            ranges = celtium$refreshReadableMappings();
            if (!ReadableMemoryRanges.containsBounds(ranges, address, end)) {
                return false;
            }
            this.readableRanges = ranges;
        }
        clientPointerAccepted++;
        return true;
    }

    /** The ITEM layout a lit draw of legacy interleaved geometry is repacked into: pos3f col4ub tex2f nrm3b pad1b. */
    static final int ITEM_FORMAT = org.soutalab.celtium.vulkan.VulkanImmediateFormats.ITEM;
    static final int ITEM_STRIDE = 28;

    /**
     * Whether a draw has an ITEM twin: the legacy 32-byte interleave (position 0, UV 12, colour 20, byte normal 24) that
     * GVCLib's Tessellator2 writes, decoded as POSITION_TEX_NORMAL (9) or POSITION_TEX_COLOR_NORMAL (8). Those carry
     * normals, but only the lit ITEM pipeline shades by them: drawn in their own layout the A4 came out flat white where
     * OpenGL lit it facet by facet (E2E leg a4-colour1 against the Celeritas reference).
     */
    static boolean hasItemTwin(int stride, int format) {
        return stride == 32 && (format == 8 || format == 9);
    }

    private static boolean lightingOn(TerrainGpuBackend backend) {
        return backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                && vulkan.fixedFunctionLightingEnabled();
    }

    /**
     * Repacks the legacy 32-byte interleave into ITEM order. Without a colour array the vertex colour is opaque white,
     * so the lit shader's group colour - the Mod's glColor - is the only tint, as in fixed-function lighting with
     * GL_COLOR_MATERIAL.
     */
    static ByteBuffer copyClientVerticesAsItem(long address, int count, int format) {
        ByteBuffer copy = MemoryUtil.memAlloc(Math.multiplyExact(count, ITEM_STRIDE));
        long target = memAddress(copy);
        for (int i = 0; i < count; i++) {
            long from = address + (long) i * 32;
            long to = target + (long) i * ITEM_STRIDE;
            MemoryUtil.memCopy(from, to, 12);
            MemoryUtil.memPutInt(to + 12, format == 8 ? MemoryUtil.memGetInt(from + 20) : -1);
            MemoryUtil.memCopy(from + 12, to + 16, 8);
            MemoryUtil.memCopy(from + 24, to + 24, 4);
        }
        return copy;
    }

    /**
     * Validates the split arrays for a stride-48 draw and arms the repack with their per-draw bases.
     * <p>
     * Asked at the format decision, not at the copy: the first version validated later, and the draw that reached the
     * copy through a path without that validation copied from address zero and took the JVM down with SIGSEGV. The
     * acceptance of the layout and the validity of its pointers are the same decision.
     */
    private boolean celtium$splitArraysValid(int sourceStride, int count, int first, boolean clientMemory,
            long storageBase, int storageCapacity, String[] refusalOut) {
        if (first < 0 || count <= 0 || !celtium$texColorPointersKnown()) {
            return false;
        }
        int texStride = this.texCoordPointerStride > 0 ? this.texCoordPointerStride : 8;
        boolean floatColor = this.colorPointerType == GL_FLOAT;
        int colorStride = this.colorPointerStride > 0 ? this.colorPointerStride : (floatColor ? 16 : 4);
        long texBytes = (long) (count - 1) * texStride + 8L;
        long colorBytes = (long) (count - 1) * colorStride + (floatColor ? 16L : 4L);
        long texFirstBytes = (long) first * texStride;
        long colorFirstBytes = (long) first * colorStride;
        long texFrom;
        long colorFrom;
        if (clientMemory) {
            try {
                texFrom = Math.addExact(this.texCoordPointerOffset, texFirstBytes);
                colorFrom = Math.addExact(this.colorPointerOffset, colorFirstBytes);
            } catch (ArithmeticException overflow) {
                return false;
            }
            if (!celtium$readableMapping(texFrom, texBytes)) {
                refusalOut[0] = "the split texture array at 0x" + Long.toHexString(this.texCoordPointerOffset)
                        + " is not readable for " + count + " vertices";
                return false;
            }
            if (!celtium$readableMapping(colorFrom, colorBytes)) {
                refusalOut[0] = "the split colour array at 0x" + Long.toHexString(this.colorPointerOffset)
                        + " is not readable for " + count + " vertices";
                return false;
            }
        } else {
            // A VBO-bound draw passes offsets into the bound buffer, and the stub owns a mirror of its contents: the
            // addresses are mirror base + offset. Measured: Immersive Railroading's stride-48 item draws are VBO-backed,
            // which is why requiring client memory refused them again after the projection guard was relaxed.
            if (this.texCoordPointerOffset < 0L || this.colorPointerOffset < 0L) {
                refusalOut[0] = "the split arrays are at negative offsets " + this.texCoordPointerOffset + " and "
                        + this.colorPointerOffset;
                return false;
            }
            if (this.texCoordPointerOffset > storageCapacity - texFirstBytes
                    || mapRangeOffset(this.texCoordPointerOffset + texFirstBytes, texBytes, storageCapacity) < 0L) {
                refusalOut[0] = "the split texture array reads past the end of a " + storageCapacity + "-byte buffer";
                return false;
            }
            if (this.colorPointerOffset > storageCapacity - colorFirstBytes
                    || mapRangeOffset(this.colorPointerOffset + colorFirstBytes, colorBytes, storageCapacity) < 0L) {
                refusalOut[0] = "the split colour array reads past the end of a " + storageCapacity + "-byte buffer";
                return false;
            }
            texFrom = storageBase + this.texCoordPointerOffset + texFirstBytes;
            colorFrom = storageBase + this.colorPointerOffset + colorFirstBytes;
        }
        this.texColorRepackTexBase = texFrom;
        this.texColorRepackColorBase = colorFrom;
        this.texColorRepackSourceStride = sourceStride;
        this.texColorRepackArmed = true;
        return true;
    }

    /** Whether the split arrays describe the layout the stride-48 repack can build. */
    private boolean celtium$texColorPointersKnown() {
        boolean colorOk = (this.colorPointerType == GL_FLOAT && this.colorPointerSize == 4)
                || (this.colorPointerType == GL_UNSIGNED_BYTE && this.colorPointerSize == 4);
        return this.texCoordPointerType == GL_FLOAT && this.texCoordPointerSize == 2
                && this.texCoordPointerOffset != 0L && colorOk && this.colorPointerOffset != 0L;
    }

    /** Takes the repack for the stride-48 split layout, and the ordinary copy for every other layout. */
    private ByteBuffer celtium$copyClientVerticesForDraw(long address, int count, int stride, int format) {
        if (format == FORMAT_POSITION_TEX_COLOR && this.texColorRepackArmed) {
            return celtium$repackPaddedTexColor(address, this.texColorRepackSourceStride, count);
        }
        return copyClientVertices(address, count, stride, format);
    }

    /**
     * Builds POSITION_TEX_COLOR vertices (position, float2 texture, 4 unsigned-byte colour) from the split client arrays
     * of a stride-48 draw. The bases were advanced by {@code first} and their ranges validated by the caller.
     */
    private ByteBuffer celtium$repackPaddedTexColor(long vertexAddress, int sourceStride, int count) {
        int texStride = this.texCoordPointerStride > 0 ? this.texCoordPointerStride : 8;
        boolean floatColor = this.colorPointerType == GL_FLOAT;
        int colorStride = this.colorPointerStride > 0 ? this.colorPointerStride : (floatColor ? 16 : 4);
        ByteBuffer out = MemoryUtil.memAlloc(Math.multiplyExact(count, 24));
        long to = MemoryUtil.memAddress(out);
        for (int i = 0; i < count; i++) {
            long target = to + (long) i * 24;
            MemoryUtil.memCopy(vertexAddress + (long) i * sourceStride, target, 12);
            MemoryUtil.memCopy(this.texColorRepackTexBase + (long) i * texStride, target + 12, 8);
            long color = this.texColorRepackColorBase + (long) i * colorStride;
            if (floatColor) {
                for (int c = 0; c < 4; c++) {
                    float value = MemoryUtil.memGetFloat(color + (long) c * 4L);
                    int byteValue = value <= 0.0F ? 0 : value >= 1.0F ? 255 : (int) (value * 255.0F + 0.5F);
                    MemoryUtil.memPutByte(target + 20 + c, (byte) byteValue);
                }
            } else {
                MemoryUtil.memCopy(color, target + 20, 4);
            }
        }
        this.texColorRepacks++;
        if (this.texColorRepackDumps < 2) {
            this.texColorRepackDumps++;
            System.out.println("[Celtium] CELTIUM_CLIENT_ARRAY_REPACK layout=pos3f+tex2f+col4 fromStride="
                    + sourceStride + " toStride=24"
                    + " vertices=" + count + " colorType=0x" + Integer.toHexString(this.colorPointerType)
                    + " texCoordType=0x" + Integer.toHexString(this.texCoordPointerType));
        }
        return out;
    }

    /** Takes ownership at compile/submit time; no replay may read the caller's reused array. */
    static ByteBuffer copyClientVertices(long address, int count, int stride, int format) {
        int targetStride = stride == 32 && format == 9 ? 24 : stride == 32 && format == 8 ? 28 : stride;
        ByteBuffer copy = MemoryUtil.memAlloc(Math.multiplyExact(count, targetStride));
        long target = memAddress(copy);
        if (stride == 32 && format == 9) {
            for (int i = 0; i < count; i++) {
                MemoryUtil.memCopy(address + (long) i * stride, target + (long) i * targetStride, 20);
                MemoryUtil.memCopy(address + (long) i * stride + 24, target + (long) i * targetStride + 20, 4);
            }
        } else if (stride != targetStride) {
            for (int i = 0; i < count; i++)
                MemoryUtil.memCopy(address + (long) i * stride, target + (long) i * targetStride, targetStride);
        } else MemoryUtil.memCopy(address, target, (long) count * stride);
        return copy;
    }

    /** Retires a range of display-list names: payloads, live-name record and the model capture's geometry. */
    private void deleteListsRange(int first, int range) {
        org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listsDeleted(first, range);
        for (int i = 0; i < range; i++) {
            Integer name = Integer.valueOf(first + i);
            GlsmDisplayListLedger.current().removeList(name.intValue());
            GlsmNameLedger.current().removeName(name.intValue());
            releaseListPayloads(name);
        }
    }

    /** {@code glListBase} from the transformer's route. */
    public static void celtium$listBase(int base) {
        GlsmHintLedger.current().noteArrival("glListBase");
        CeltiumGlStub stub = installed;
        if (stub != null) {
            GlsmDisplayListLedger.current().listBase(base);
        }
    }

    /** {@code glCallLists} from the transformer's route. */
    public static void celtium$callLists(int count, int type, long address) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.callLists(count, type, address);
        }
    }

    /** {@code glDeleteLists} from the transformer's route. */
    public static void celtium$deleteLists(int first, int range) {
        CeltiumGlStub stub = installed;
        if (stub != null && first > 0 && range > 0) {
            stub.deleteListsRange(first, range);
        }
    }

    /** {@code glIsTexture/glIsBuffer/...} from the transformer's route: whether the name was generated here. */
    public static boolean celtium$isObject(int name) {
        CeltiumGlStub stub = installed;
        return stub != null && name != 0 && (GlsmNameLedger.current().isLive(name) || GlsmNameLedger.current().isLiveSync(name));
    }

    /** {@code glIsSync} from the transformer's route: the sync names are 64-bit. */
    public static boolean celtium$isSync(long sync) {
        CeltiumGlStub stub = installed;
        return stub != null && sync != 0L && GlsmNameLedger.current().isLiveSync(sync);
    }

    /** {@code glIsList} from the transformer's route: whether this stub compiled the name. */
    public static boolean celtium$isList(int list) {
        CeltiumGlStub stub = installed;
        return stub != null && GlsmNameLedger.current().isLive(list);
    }

    /** Upper bound on one glCallLists run: a malformed count must not stall the render thread. */
    private static final int MAX_CALL_LIST_NAMES = 1 << 16;

    /**
     * Executes a {@code glCallLists} run: every name is the list base plus the value read at that index. Vanilla does
     * not call this, but font and model batching mods do, and the call was a no-op - their lists produced no picture.
     */
    private void callLists(int count, int type, long address) {
        if (count <= 0 || address == 0L) {
            return;
        }
        // The count is caller data; a bogus one loops for as long as it says. Replaying the first 64 Ki names is
        // bounded work and still draws what a legitimate batch of that size would.
        if (count > MAX_CALL_LIST_NAMES) {
            count = MAX_CALL_LIST_NAMES;
        }
        if (type == 0x1407 || type == 0x1408 || type == 0x1409) {
            // GL_2_BYTES/3/4: the element is a group of that many bytes, and *each byte* is one list offset - the
            // legacy font-list shape, where a 4-byte element names four lists.
            int group = type - 0x1405;
            for (int index = 0; index < count; index++) {
                for (int byteIndex = 0; byteIndex < group; byteIndex++) {
                    int offset = org.lwjgl.system.MemoryUtil.memGetByte(
                            address + (long) index * group + byteIndex) & 0xFF;
                    replayDisplayList(GlsmDisplayListLedger.current().listBase() + offset);
                }
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            int offset;
            switch (type) {
                case 0x1400: offset = org.lwjgl.system.MemoryUtil.memGetByte(address + i); break; // GL_BYTE
                case 0x1401: offset = org.lwjgl.system.MemoryUtil.memGetByte(address + i) & 0xFF; break; // GL_UNSIGNED_BYTE
                case 0x1402: offset = org.lwjgl.system.MemoryUtil.memGetShort(address + (long) i * 2L); break; // GL_SHORT
                case 0x1403: offset = org.lwjgl.system.MemoryUtil.memGetShort(address + (long) i * 2L) & 0xFFFF; break;
                case 0x1404: // GL_INT
                case 0x1405: offset = org.lwjgl.system.MemoryUtil.memGetInt(address + (long) i * 4L); break; // GL_UNSIGNED_INT
                case 0x1406: offset = (int) org.lwjgl.system.MemoryUtil.memGetFloat(address + (long) i * 4L); break;
                default: return;
            }
            replayDisplayList(GlsmDisplayListLedger.current().listBase() + offset);
        }
    }

    private void replayDisplayList(int listId) {
        // The replay, and the reason this field exists at all. Minecraft compiles a model once
        // (ModelRenderer.compileDisplayList, under GL_COMPILE) and draws it with glCallList(displayList);
        // VulkanModelQuadCapture's hook sits inside that compilation, so it saw the recording and refused the
        // quads with `reason=unknown-list list=0`, and the draw itself reached this no-op. Entities, the
        // player, held items, the sky, the stars and the horizon plane all arrive here.
        GlsmDisplayListLedger.current().noteCalled(listId);
        List<Object[]> list = GlsmDisplayListLedger.current().list(listId);
        if (list == null) {
            // The spec: an undefined list is ignored, not an error. Counted so a silent miss is visible - and filed
            // under the id that missed, because "12 unresolved" says nothing about which lists a caller expected.
            //
            // Two different events used to share this counter, and the measured run says why they must not: the entity
            // leg r41a-entities reported `unresolved=1250` and every one of them was list -1, which is what
            // ModelRenderer passes for a part whose list was never compiled - so the number read as 1250 lost draws
            // when it was 1250 calls GL defines as no-ops. A real name that resolves to nothing is a lost draw and is
            // counted as `unresolved`; a non-positive name is the caller's own "no list" sentinel and is counted as
            // `noList`, which is a fact about the caller rather than about the renderer.
            if (listId <= 0) {
                listCallsWithoutAList++;
            } else {
                GlsmDisplayListLedger.current().noteUnresolved();
                ListCensus.UNRESOLVED.addTo(listId, 1);
            }
        } else if (this.replayDepth < MAX_LIST_NESTING) {
            this.replayDepth++;
            int outerExecutingList = GlsmDisplayListLedger.current().executingListId();
            GlsmDisplayListLedger.current().executingListId(listId);
            try {
                for (int i = 0; i < list.size(); i++) {
                    Object[] call = list.get(i);
                    if (call[0] == CompiledLegacyRun.TAG) {
                        // A glBegin run compiled into this list: submitted now, in order, so the state the recorded
                        // commands around it left is the state it draws under - which is what a display list does.
                        replayLegacyRun((CompiledLegacyRun) call[1]);
                        continue;
                    }
                    Fn command = (Fn) call[0];
                    if (command.semantics == SEM_COMPILED_ARRAYS) replayCompiledArrays((long[]) call[1]);
                    else dispatchArgs(command, (long[]) call[1]);
                }
            } finally {
                this.replayDepth--;
                // Restored to the outer list on the way out, and cleared only at the outermost exit: a quad
                // drawn after the replay is not filed under the last list, and a quad the outer list draws
                // after an inner list returns is not filed under the inner one either.
                GlsmDisplayListLedger.current().executingListId(this.replayDepth == 0 ? 0 : outerExecutingList);
            }
            GlsmDisplayListLedger.current().noteReplayed();
            noteListReplayed(listId, list.size());
        } else {
            // The spec's floor for the nesting limit is 64 and this class stops there. A stopped replay used to be
            // counted nowhere at all - displayListsReplayed only counted the ones that ran - so a recursive list was
            // an invisible loss rather than a named one.
            listReplayNestingOverflow++;
        }
    }


    private void replayCompiledArrays(long[] c) {
        try {
            TerrainGpuBackend backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            boolean lit = c.length > 5 && c[5] != 0L && lightingOn(backend);
            NativeGeometryBuffer geometry = NativeGeometryBuffer.find((int) (lit ? c[5] : c[0]));
            boolean taken = geometry != null && backend != null && (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend
                    ? ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) backend).submitRetainedGeometry(geometry, (int) c[4])
                    : backend.submitImmediateBatch(null, lit ? ITEM_STRIDE : (int) c[1], lit ? ITEM_FORMAT : (int) c[2],
                            geometry.address(), (int) c[3], (int) c[4]));
            if (taken) {
                GlDrawCensus.DRAWARRAY_DRAWN.incrementAndGet();
                GlDrawCensus.DRAWARRAY_VERTICES.addAndGet(c[3]);
            } else GlDrawCensus.celtium$noteDrawArrayRefusal("compiled client arrays: "
                    + (backend == null ? "no backend" : backend.lastRefusal()));
        } catch (RuntimeException | LinkageError error) {
            GlDrawCensus.celtium$noteDrawArrayRefusal("compiled client arrays: " + error);
        }
    }

    /** The cached mapping list, refreshed on a miss; volatile because the list is replaced, never mutated. */
    private volatile ReadableMemoryRanges.Range[] readableRanges;
    /**
     * How often the list has been re-read, and when the last read happened. The count is a census; the only
     * bound on refreshing is the interval below. A lifetime cap stood here before (64 reads): a long run
     * churns its mappings - JIT code, arenas and file maps come and go - so the cap froze the list for good
     * and the guard went on answering from a mapping layout the process no longer had.
     */
    private int readableMappingRefreshes;
    private long readableMappingLastRefreshNanos;
    /** Read once: addressFor runs for every GL entry point, and a property lookup there is a per-call map lookup. */
    private static final boolean TRACE_GL_CALLS = Boolean.getBoolean("celtium.glstub.trace");
    /** Same reasoning: the strict-symbol check is consulted on the unresolved-symbol path of every dispatch. */
    private static final boolean STRICT_SYMBOLS = Boolean.getBoolean("celtium.vulkan.strictSymbols");
    private static final long READABLE_MAPPING_REFRESH_INTERVAL_NANOS = 100L * 1000L * 1000L;

    private ReadableMemoryRanges.Range[] celtium$refreshReadableMappings() {
        if (!this.readableRangeProvider.available()) {
            return new ReadableMemoryRanges.Range[0];
        }
        long now = System.nanoTime();
        if (this.readableMappingRefreshes > 0
                && now - this.readableMappingLastRefreshNanos < READABLE_MAPPING_REFRESH_INTERVAL_NANOS) {
            ReadableMemoryRanges.Range[] current = this.readableRanges;
            return current == null ? new ReadableMemoryRanges.Range[0] : current;
        }
        this.readableMappingRefreshes++;
        this.readableMappingLastRefreshNanos = now;
        ReadableMemoryRanges.Range[] array;
        try {
            array = ReadableMemoryRanges.normalize(this.readableRangeProvider.read());
        } catch (Throwable error) {
            readableMappingFailure = String.valueOf(error);
            return new ReadableMemoryRanges.Range[0];
        }
        this.readableRanges = array;
        readableMappingRanges = array.length;
        return array;
    }

    /**
     * Diagnostics for the mapping guard: how many client-memory draws it let through, how many readable ranges the
     * last platform mapping read found, and why that read failed if it did. Static because the census line is static,
     * and one process has one mapping list.
     * <p>
     * The mapping guard is a <em>guard</em>, not an ownership record: it answers "is this range readable right now",
     * not "who owns it, how long does it live, or is it still the buffer the game meant". The authoritative
     * client-memory entry point is the owned-buffer publication below; these two counters exist to keep the two apart
     * in a run.
     */
    private static volatile long clientPointerAccepted;
    private static volatile long ownedClientBufferDecoded;
    private static volatile long pointerOnlyDecoded;
    private static volatile long ownedClientBufferMismatch;
    private static volatile int readableMappingRanges = -1;
    private static volatile String readableMappingFailure = "none";

    /**
     * The game's own vertex buffer for the client-array draw that is about to be made from it (B09).
     * <p>
     * What this replaces: a client pointer is an address with no length and no owner, so the stub could only read it
     * behind the {@code /proc/self/maps} guard, cap the range at 32 MiB, and refuse by name where that file cannot be
     * read (any non-Linux OS) - and a mapping hit still says nothing about the buffer's lifetime. The uploader mixin
     * ({@code ClientArrayBufferOwnershipMixin}) has the {@code BufferBuilder} itself in hand one instruction before
     * {@code GlStateManager.glDrawArrays}, so it publishes the buffer's address <em>and its capacity</em> here. The
     * next draw whose range is inside that buffer needs no guard: the length is the buffer's own, and the process
     * demonstrably owns the memory.
     * <p>
     * Thread-local and consumed once per draw: two render threads may each be inside their own uploader, and a
     * publication that outlived its draw would be read as the next draw's ownership claim. The slot is reused rather
     * than reallocated - this is on the per-batch path, measured at tens of thousands of calls per run.
     */
    private static final ThreadLocal<long[]> OWNED_CLIENT_BUFFER =
            ThreadLocal.withInitial(() -> new long[3]);

    /**
     * Publishes the game's own vertex buffer for the client-array draw that follows. Called by the uploader mixin with
     * the {@code BufferBuilder}'s {@code ByteBuffer} address and capacity; a zero address or a zero length is ignored,
     * so a caller cannot publish an empty claim.
     */
    public static void publishClientArrayBuffer(long address, long bytes) {
        if (address == 0L || bytes <= 0L) {
            return;
        }
        long[] slot = OWNED_CLIENT_BUFFER.get();
        slot[0] = address;
        slot[1] = bytes;
        slot[2] = 1L;
    }

    /** {@return the published owned buffer as {address, bytes} once, or null when none is pending}. */
    private static long[] celtium$consumeOwnedClientBuffer() {
        long[] slot = OWNED_CLIENT_BUFFER.get();
        if (slot == null || slot[2] == 0L) {
            return null;
        }
        slot[2] = 0L;
        return slot;
    }

    /** {@return how many draws the owned-buffer publication decoded, for the mixin's own census}. */
    public static long ownedClientBufferDraws() {
        return ownedClientBufferDecoded;
    }

    /**
     * One line per distinct (client, stride, format, count) tuple that reaches the backend, bounded, printed once.
     * <p>
     * The client-memory case is new and its layouts come from a user's launch rather than from this fixture, so what it
     * decodes has to be attributable in the census: a line here says which stride and format the draw was answered
     * with, and the array-buffer case keeps its own {@code CELTIUM_DRAWARRAY_LAYOUT} line at the first accepted draw.
     */
    private static final java.util.Set<String> CLIENT_TUPLES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void celtium$noteClientTuple(boolean clientMemory, int buffer, int count, int formatId) {
        if (!clientMemory
                || !org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(CLIENT_TUPLES.size(), 12)) {
            return;
        }
        String key = VERTEX_POINTER.stride + "/" + formatId + "/" + celtium$otherArrayMask();
        if (!CLIENT_TUPLES.add(key)) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_CLIENT_ARRAY stride=" + VERTEX_POINTER.stride
                + " format=" + formatId + " otherArrays=" + celtium$otherArrayNames()
                + " count=" + count + " pointer=0x" + Long.toHexString(VERTEX_POINTER.offset)
                + " readableRanges=" + readableMappingRanges
                + " mappingFailure=" + readableMappingFailure
                + " site=[" + celtium$callSite(8) + "]");
    }

    /** Distinct (size,type,stride) vertex-pointer tuples already described, bounded to twelve lines. */
    private static final java.util.Set<Integer> VERTEX_POINTER_SITES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Buffers whose first accepted draw has already been described, bounded: one line per distinct array buffer. */
    private static final java.util.Set<Integer> DRAWARRAY_LAYOUTS_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /**
     * Accepted draws still to be described in full, counted down.
     * <p>
     * Per-buffer lines said what the buffers are; this says what colour each accepted draw is made in. The group
     * takes its colour when it opens, so a probe that prints the mirror's colour at the draw is printing the value
     * the group will carry - and a format with no colour in its vertices is drawn entirely in that value.
     */
    private static final java.util.concurrent.atomic.AtomicInteger DRAWARRAY_PROBE_LINES =
            new java.util.concurrent.atomic.AtomicInteger(
                    Integer.getInteger("celtium.glstub.drawArrayProbeLines", 12).intValue());
    /**
     * Accepted draws whose group colour has red and green at exactly zero.
     * <p>
     * The colour of a format-7 batch is not in its vertices: the group takes it when it opens. So a frame that shows
     * pure blue (0,0,B) geometry can only have got it from a group whose colour is pure blue, and counting those among
     * the accepted draws is what tells "the immediate path drew it in that colour" from "the immediate path displaced
     * something else that drew it". One number, and the first three sites when it is not zero.
     */
    // DRAWARRAY_PURE_BLUE itself is owned by GlDrawCensus now (giant-split seam 5, QUEUE 653).
    private static final java.util.concurrent.atomic.AtomicInteger DRAWARRAY_PURE_BLUE_LOGGED =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * {@return whether the first accepted draw from this buffer still needs its one line}.
     * <p>
     * Bounded on purpose: {@code DrawArraysCensus} lines are per buffer, not per draw, so a run of ten thousand draws
     * adds three or four lines rather than ten thousand.
     */
    private static boolean celtium$noteDrawArrayLayout(int buffer) {
        return org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(DRAWARRAY_LAYOUTS_LOGGED.size(), 8)
                && DRAWARRAY_LAYOUTS_LOGGED.add(Integer.valueOf(buffer));
    }

    /**
     * {@return the matrices the immediate path will draw the batch with, read from the renderer's own mirror}.
     * <p>
     * This is a probe, not a decision: it is read once per buffer and never gates a draw. It exists because the frame
     * cannot tell "the vertices are not the sky" from "the sky is drawn with the identity matrix" - and the second is
     * a property of the renderer's matrix channel, not of this class's decoding.
     */
    private static String celtium$mirroredMatrices(TerrainGpuBackend backend) {
        try {
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                float[] projection = vulkan.celtium$mirroredProjection();
                float[] modelView = vulkan.celtium$mirroredModelView();
                float[] colour = vulkan.celtium$mirroredColor();
                return "mirroredProjection=[" + celtium$round3(projection[0]) + "," + celtium$round3(projection[5])
                        + "," + celtium$round3(projection[10]) + "," + celtium$round3(projection[11]) + ","
                        + celtium$round3(projection[14]) + "," + celtium$round3(projection[15]) + "]"
                        + " mirroredModelViewT=(" + celtium$round3(modelView[12]) + "," + celtium$round3(modelView[13])
                        + "," + celtium$round3(modelView[14]) + ")"
                        + " mirroredColor=(" + celtium$round3(colour[0]) + "," + celtium$round3(colour[1]) + ","
                        + celtium$round3(colour[2]) + "," + celtium$round3(colour[3]) + ")";
            }
        } catch (RuntimeException | LinkageError error) {
            return "mirroredMatrices=unreadable(" + error + ")";
        }
        return "mirroredMatrices=not-a-vulkan-backend";
    }

    /**
     * Counts accepted draws whose group colour is pure blue (red and green exactly zero), and names the first three.
     * <p>
     * The question it was built to answer: the presented image gained ~170,000 pixels whose red and green are exactly
     * zero while this path was enabled, and a flat-coloured shader can only produce those from a group colour of
     * (0,0,B). <b>It answered {@code pureBlueGroups=0} over 64 accepted draws while the frame held 176,633 such
     * pixels</b>, and that negative is the measurement that mattered: the group colour never was pure blue, so the
     * colour did not come from the push block at all. It came from the vertex data, read as a colour attribute by a
     * pipeline that had been built as {@code POSITION_TEX_COLOR} whatever variant it was asked for - see
     * {@code VulkanImmediateTexturePipeline.allocate}. Kept because it is
     * still the only counter that would catch a *deliberate* pure-blue group, but it must never again be read as
     * "the path did not paint those pixels": the instrument's premise (the drawn colour is the group colour) is
     * exactly what was false.
     */
    private static void celtium$noteAcceptedColor(TerrainGpuBackend backend, int buffer, int count) {
        try {
            if (!(backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan)) {
                return;
            }
            if (!vulkan.celtium$mirroredColorRedGreenZero()) {
                return;
            }
            GlDrawCensus.DRAWARRAY_PURE_BLUE.incrementAndGet();
            if (DRAWARRAY_PURE_BLUE_LOGGED.incrementAndGet() <= 3) {
                float[] colour = vulkan.celtium$mirroredColor();
                System.out.println("[Celtium] CELTIUM_DRAWARRAY_PUREBLUE buffer=" + buffer + " count=" + count
                        + " colour=(" + celtium$round3(colour[0]) + "," + celtium$round3(colour[1]) + ","
                        + celtium$round3(colour[2]) + "," + celtium$round3(colour[3]) + ") site=["
                        + celtium$callSite(8) + "]");
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A probe that fails is a measurement lost, never a frame lost.
        }
    }

    /** Three decimals, which is the precision the renderer's own matrix lines use. */
    private static float celtium$round3(float value) {
        return (float) (Math.round(value * 1000.0D) / 1000.0D);
    }


    /**
     * {@return why this batch must not be submitted, or {@code null} when its transform is usable}.
     * <p>
     * The vertices can be decoded perfectly and the draw still be wrong: the transform the immediate path applies is
     * the renderer's mirrored projection times its mirrored model-view, and under Cleanroom's Kirino the projection
     * mirror misses the world's real projection. Kirino calls {@code GlStateManager.loadIdentity} (mirrored) before
     * each pass and then its own {@code Project.gluPerspective}, which never passes through {@code GlStateManager} -
     * so between those two calls the mirror holds the identity while GL holds the perspective, and a batch offered in
     * that window is drawn by a matrix that collapses the scene.
     * <p>
     * Measured, not assumed: the sky dome's own vertices decode exactly as {@code RenderGlobal.renderSky} builds them
     * (a 13x13 grid of 64-block quads at y=+16, first vertex (-384, 16, -384), 676 vertices) and the mirrored
     * projection at that accepted draw was {@code [1,1,1,0,0,1]} in one run and
     * {@code [0.805,1.431,-1.001,-1.0,-0.1,0]} in another. Runs of the first kind have ~90,000 pixels of pure blue
     * (0,0,B) in the presented frame where the plane is clipped down to slivers along the frustum edges; the second
     * kind draws the plane itself. Refusing the degenerate transform keeps the wrong pixels out of the frame and
     * names the gap instead of hiding it - the projection channel is the fix, not this class.
     */
    private static String celtium$projectionRefusal(TerrainGpuBackend backend, int drawFramebuffer) {
        if (!(backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan)) {
            return null;
        }
        float[] projection;
        try {
            projection = vulkan.celtium$mirroredProjection();
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
        if (projection == null || projection.length < 16) {
            return null;
        }
        float m00 = projection[0];
        float m11 = projection[5];
        float m22 = projection[10];
        float m23 = projection[11];
        float m32 = projection[14];
        float m33 = projection[15];
        if (Math.abs(m00) < 1.0E-4F || Math.abs(m11) < 1.0E-4F) {
            return "the mirrored projection collapses x or y (m00=" + celtium$round3(m00) + " m11="
                    + celtium$round3(m11) + "), so this batch would be drawn with the wrong transform";
        }
        if (Math.abs(m00 - 1.0F) < 1.0E-4F && Math.abs(m11 - 1.0F) < 1.0E-4F && Math.abs(m22 - 1.0F) < 1.0E-4F
                && Math.abs(m23) < 1.0E-4F && Math.abs(m32) < 1.0E-4F && Math.abs(m33 - 1.0F) < 1.0E-4F) {
            // An identity projection is wrong for the window's scene and correct for a mod's off-target sprite capture:
            // Immersive Railroading draws unit-sized models into a 128x128 framebuffer at mod-reload time, before any
            // perspective exists, and expects clip space. Measured: eight such draws per run were refused here.
            if (vulkan.celtium$isMainDrawTarget(drawFramebuffer)) {
                return "the mirrored projection is the identity at draw time (Kirino's gluPerspective does not reach the"
                        + " mirror), so this batch would be drawn with the wrong transform";
            }
            return null;
        }
        return null;
    }

    /**
     * Records that a fogged batch is being drawn by an immediate path whose fog state never arrived.
     * <p>
     * This was a refusal once, and it was inverted on purpose. The branch refused a fogged draw whenever
     * {@code TerrainGpuBackend.immediateFogAvailable()} answered false, on the grounds that the batch would paint its
     * raw colour where vanilla's is the fog mix. Two things made drawing the better picture even then, and the second
     * is the stronger one:
     * <ul>
     *   <li>The dome is a plane at y=+16 seen from a camera at y=12.0 (measured: the decode prints
     *       (-384,16,-384) as its first vertex, and the fixture's camera line is y=12.0), so the fragments visible in
     *       the band are close by and vanilla's own sky there is close to the dome's raw colour - the fog ramp only
     *       reaches the fog colour at the horizon.</li>
     *   <li>A refusal under the GL-less window is drawn by nobody, and the band then keeps the frame target's clear
     *       colour, which is <em>further</em> from the reference than the batch's flat colour: the measured clear colour
     *       at the dome's draw is (0.415,0.481,0.594) = (106,123,151), the dome's own colour is
     *       (0.45,0.626,0.956) = (115,160,244), and the reference's sky band runs (149,187,255) at row 0 to
     *       (179,207,255) at row 140. Drawing is the smaller error, so the draw happens and the missing capability is
     *       reported instead of turned into a hole.</li>
     * </ul>
     * <p>
     * On the tree where this was measured the capability is present, so the line does not appear at all: the run's
     * dome drew as a gradient identical to upstream Celeritas' (rows 0-199: no pixel differs by more than 8, maximum
     * channel difference 4). The line exists for the configuration where the fog mixin does not apply - a flat sky and
     * a fogged sky are two different findings, and the frame alone cannot tell them apart.
     */
    private void celtium$noteFogDegradation(TerrainGpuBackend backend, int buffer, int count) {
        if (!org.soutalab.celtium.vulkan.ImmediateFogState.current().enabled()
                || backend.immediateFogAvailable() || SKY_DEGRADED_LINES.getAndDecrement() <= 0) {
            return;
        }
        float[] clear = new float[4];
        clearColor(clear);
        System.out.println("[Celtium] CELTIUM_SKY_DEGRADED buffer=" + buffer + " count=" + count
                + " fogEnabled=true fogAvailable=false"
                + " clearColor=(" + celtium$round3(clear[0]) + "," + celtium$round3(clear[1]) + ","
                + celtium$round3(clear[2]) + "," + celtium$round3(clear[3]) + ")"
                + " colour=(" + celtium$colourQuad(celtium$mirroredColour(backend)) + ")"
                + " reason=[drawn anyway: the band would otherwise keep the clear colour, which is further from the"
                + " reference than this batch's flat colour]");
    }

    /** Bounded, so a run records the degraded sky a few times instead of once per draw. */
    private static final java.util.concurrent.atomic.AtomicInteger SKY_DEGRADED_LINES =
            new java.util.concurrent.atomic.AtomicInteger(
                    Integer.getInteger("celtium.glstub.skyDegradedLines", 4).intValue());

    /** {@return the mirrored colour as one bracketed quad of three-decimal values}, or {@code unknown}. */
    private static String celtium$colourQuad(float[] colour) {
        if (colour == null || colour.length < 4) {
            return "unknown";
        }
        return celtium$round3(colour[0]) + "," + celtium$round3(colour[1]) + "," + celtium$round3(colour[2]) + ","
                + celtium$round3(colour[3]);
    }

    /** {@return the renderer's own colour mirror}, or {@code null} when this backend does not expose one. */
    private static float[] celtium$mirroredColour(TerrainGpuBackend backend) {
        try {
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                return vulkan.celtium$mirroredColor();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A probe that fails is a measurement lost, never a frame lost.
        }
        return null;
    }

    /**
     * {@return the game-side frames of the current call stack, innermost first}.
     * <p>
     * The same filter the uploader hook uses: the renderer's own classes and the mixin machinery are skipped, so what
     * is left is Minecraft, Forge, Celeritas' call sites and the mod that made the call. Which call site a draw came
     * from is not decoration here - the 1.12.2 sources contain four different {@code vertexbuffer.drawArrays(7)}
     * callers with three different strides, and "the sky" and "a chunk list" are told apart by the site, not by the
     * shape of the call.
     */
    static String celtium$callSite(int limit) {
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
            frames.append(celtium$shortSiteName(name)).append('.').append(frame.getMethodName()).append(':')
                    .append(frame.getLineNumber());
            if (++taken >= limit) {
                break;
            }
        }
        return frames.toString();
    }

    /**
     * Shortens a frame's class name for a diagnostic line.
     * <p>
     * The operator read a Vulkan-only session's log and asked why it was full of "opengl". The answer was these site
     * strings: every instrumented line named {@code org.lwjgl.opengl.GL11} and {@code org.lwjgl.opengl.GL11C} in full
     * while the renderer used no GL at all, so the log read as if it did. The class is still named, without the
     * package prefix that caused the misreading.
     */
    private static String celtium$shortSiteName(String className) {
        String shortened = className;
        for (String prefix : new String[] { "org.lwjgl.opengl.", "org.lwjgl.util.", "org.lwjgl.", "org.lwjglx.",
                "net.minecraftforge.", "net.minecraft." }) {
            if (shortened.startsWith(prefix)) {
                shortened = shortened.substring(prefix.length());
            }
        }
        return shortened;
    }

    /**
     * Successful-state queries: every out slot the function writes gets a plausible value. Shader/program status
     * queries answer "compiled"/"linked" so the game's program checks pass; everything else answers 0.
     */
    private void status(Fn fn, long[] c) {
        int outIndex = lastPointerArg(fn);
        if (outIndex < 0) {
            return;
        }
        long out = c[outIndex];
        if (out == 0L) {
            return;
        }
        int pname = fn.args.length > 1 ? (int) c[1] : 0;
        if (fn.name.startsWith("glGetTextureLevelParameter")) {
            // DSA form: (texture, level, pname, out) - the texture name is given, so no binding lookup is needed.
            int value = texLevelParamForName((int) c[0], (int) c[1], (int) c[2]);
            if (fn.name.endsWith("fv")) {
                MemoryUtil.memPutFloat(out, value);
            } else {
                memPutInt(out, value);
            }
            return;
        }
        if (fn.name.startsWith("glGetTextureParameter")) {
            // (texture, pname, out): the filter and wrap state the mirror announces from. Answered from the record the
            // setters fill, so a texture the game asked to REPEAT is announced as REPEAT; a pname the game never set
            // answers GL's own initial state (REPEAT wrap, LINEAR mag, NEAREST_MIPMAP_LINEAR min), which is what a
            // fresh texture object really has - the hardcoded CLAMP_TO_EDGE this used to answer was not GL's default
            // and is what made the tiled options background sample as one stretched tile.
            int textureName = (int) c[0];
            int paramPname = (int) c[1];
            int value = textureParameterValue(textureName, paramPname);
            celtium$noteTextureParameterQuery(fn.name, textureName, paramPname, value,
                    textureParameterRecorded(textureName, paramPname));
            if (fn.name.endsWith("fv")) {
                MemoryUtil.memPutFloat(out, value);
            } else {
                memPutInt(out, value);
            }
            return;
        }
        if (fn.name.startsWith("glGetTexParameter")) {
            getTexParameterQuery(fn.name, (int) c[0], (int) c[1], out);
            return;
        }
        if (fn.name.startsWith("glGetNamedFramebufferAttachmentParameter")
                || fn.name.startsWith("glGetFramebufferAttachmentParameter")) {
            // (target-or-fbo, attachment, pname, out). A colour attachment the game itself reported is answered with
            // its texture: a mod that created a framebuffer and asks whether it is complete otherwise reads "no
            // attachment" for an attachment this renderer is drawing into.
            boolean named = fn.name.startsWith("glGetNamed");
            int framebuffer = named ? (int) c[0] : ((int) c[0] == 0x8CA8 ? this.readFbo : this.drawFbo);
            int attachment = (int) c[1];
            int paramPname = (int) c[2];
            int slot = colorAttachmentSlot(attachment);
            int colorTexture = 0;
            if (framebuffer != 0 && slot >= 0) {
                colorTexture = GlsmFramebufferAttachments.current().colorSlot(framebuffer, slot);
            }
            boolean colorAttached = colorTexture != 0;
            int value;
            if (paramPname == 0x8CD0) { // GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
                value = colorAttached ? 0x1702 : 0; // GL_TEXTURE / GL_NONE
            } else if (paramPname == 0x8CD1) { // GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
                value = colorAttached ? colorTexture : 0;
            } else {
                value = 0;
            }
            memPutInt(out, value);
            return;
        }
        if (fn.name.startsWith("glGetTexLevelParameter")) {
            int value = texLevelParam((int) c[0], (int) c[1], (int) c[2]);
            if (fn.name.endsWith("fv")) {
                MemoryUtil.memPutFloat(out, value);
            } else {
                memPutInt(out, value);
            }
            return;
        }
        if (fn.name.startsWith("glGetBufferParameter")) {
            getBufferParameterQuery(fn.name, (int) c[0], (int) c[1], out);
            return;
        }
        if ("glGetShaderiv".equals(fn.name) || "glGetProgramiv".equals(fn.name)) {
            // The recorded link's answer for a program the registry knows, the default status answer
            // otherwise; the info-log length comes from the recorded failure. Everything else
            // (attached counts...) -> 0. See shaderProgramQueryAnswer.
            memPutInt(out, shaderProgramQueryAnswer((int) c[0], pname));
            return;
        }
        memPutInt(out, 0);
    }

    /**
     * Info-log queries: write the empty string - except the info log of a program whose recorded link
     * failed, which is the recorded failure, bounded by the caller's bufSize (the int after the object).
     * The length out-arg gets the characters written. The other SEM_INFO_LOG names (shader source,
     * resource names) keep the empty answer; only the three log functions carry a log.
     */
    private void infoLog(Fn fn, long[] c) {
        int last = lastPointerArg(fn);
        if (last < 0) {
            return;
        }
        long log = c[last];
        int written = 0;
        if (log != 0L) {
            if ("glGetShaderInfoLog".equals(fn.name) || "glGetProgramInfoLog".equals(fn.name)
                    || "glGetInfoLogARB".equals(fn.name)) {
                written = celtium$writeInfoLog((int) c[0], c.length > 1 ? (int) c[1] : 0, log);
            } else {
                MemoryUtil.memPutByte(log, (byte) 0);
            }
        }
        // The optional length out is the pointer slot before the buffer, when it exists.
        for (int i = last - 1; i >= 0; i--) {
            if (fn.args[i] == A_PTR) {
                long lenOut = c[i];
                if (lenOut != 0L && lenOut != log) {
                    memPutInt(lenOut, written);
                }
                break;
            }
        }
    }
}
