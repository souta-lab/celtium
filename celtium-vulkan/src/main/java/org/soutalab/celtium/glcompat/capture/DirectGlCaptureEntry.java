package org.soutalab.celtium.glcompat.capture;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.FunctionProvider;

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
}
