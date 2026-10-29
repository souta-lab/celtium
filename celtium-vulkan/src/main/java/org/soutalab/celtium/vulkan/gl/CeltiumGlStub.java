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
    private static final byte R_VOID = 0;
    private static final byte R_INT = 1;
    private static final byte R_LONG = 2;
    private static final byte R_PTR = 3;
    private static final byte R_FLOAT = 4;
    private static final byte R_DOUBLE = 5;

    /** Argument types as they arrive at the trampoline: every arg slot is a pointer to the value. */
    private static final byte A_INT = 11;
    private static final byte A_LONG = 12;
    private static final byte A_PTR = 13;
    private static final byte A_FLOAT = 14;
    private static final byte A_DOUBLE = 15;
    private static final byte A_UINT = 16;

    // Semantics ids - what dispatch() does with the call. The enum keeps the table lines short.
    static final int SEM_NOOP = 0;            // record only - state the Vulkan side owns or nobody reads
    static final int SEM_GEN = 1;             // object-name allocation: returns the next id
    private static final int SEM_GET_ERROR = 2;       // always GL_NO_ERROR - nothing the stub does can raise one
    private static final int SEM_GET_STRING = 3;      // arg0 pname -> interned ascii
    static final int SEM_IS_ENABLED = 4;      // arg0 cap -> recorded enable bit
    private static final int SEM_ENABLE = 5;          // arg0 cap -> set enable bit
    private static final int SEM_DISABLE = 6;         // arg0 cap -> clear enable bit
    static final int SEM_GET_INTEGERV = 7;    // arg0 pname, arg1 out[] -> plausible ints
    private static final int SEM_GET_FLOATV = 8;      // arg0 pname, arg1 out[] -> plausible floats
    private static final int SEM_GET_BOOLEANV = 9;    // arg0 pname, arg1 out[] -> plausible bytes
    private static final int SEM_IS_OBJECT = 10;      // arg0 id -> whether the table generated it
    static final int SEM_DELETE = 11;         // id list -> retire names (reissue is the same counter-free)
    private static final int SEM_GET_STRINGI = 12;    // pname, index -> extension string
    private static final int SEM_GET_INTEGER64V = 13; // pname, out64[] -> plausible longs
    private static final int SEM_ROUTE = 14;          // pixel/semantic routing - handled in route()
    private static final int SEM_ZERO = 15;           // int return: 0 - valid-but-empty answers (uniform loc etc)
    private static final int SEM_FBO_COMPLETE = 16;   // int return: GL_FRAMEBUFFER_COMPLETE
    private static final int SEM_SIGNALED = 17;       // int return: GL_ALREADY_SIGNALED
    private static final int SEM_MAP = 18;            // ptr return: the named buffer's storage
    private static final int SEM_UNMAP = 19;          // byte return: true, buffer contents already live
    private static final int SEM_BUFFER_DATA = 20;    // (target,size,data,usage) -> size bytes of storage
    private static final int SEM_STATUS = 21;         // (obj,pname,out[]) -> successful-answer queries
    private static final int SEM_INFO_LOG = 22;       // (obj,bufSize,len*,log) -> empty string
    private static final int SEM_BIND_BUFFER = 23;    // (target,name) -> binding record for map/data
    private static final int SEM_BIND_TEXTURE = 24;   // (target,name) -> texture binding record
    private static final int SEM_ACTIVE_TEXTURE = 25; // (unit) -> texture unit selector record
    private static final int SEM_TEXIMAGE = 26;       // (target,level,internal,w,h,...) -> size record per bound
    private static final int SEM_VIEWPORT = 27;       // (x,y,w,h) -> viewport record feeding GL_VIEWPORT
    private static final int SEM_FOG_I = 71;
    private static final int SEM_FOG_F = 72;
    private static final int SEM_FOG_FV = 73;
    private static final int SEM_FOG_IV = 74;
    private static final int SEM_TEX_ENV_I = 47;      // (target,pname,param) -> texture-env int, per active unit
    private static final int SEM_TEX_ENV_F = 48;      // (target,pname,param) -> texture-env float
    private static final int SEM_TEX_ENV_FV = 49;     // (target,pname,values) -> texture-env float array
    private static final int SEM_GET_TEX_ENV_I = 50;  // (target,pname,out) -> the recorded int
    private static final int SEM_GET_TEX_ENV_FV = 51; // (target,pname,out) -> the recorded floats
    private static final int SEM_COPY_TEX_IMAGE = 52;    // copy from the bound read framebuffer into a texture level
    private static final int SEM_COPY_TEX_SUB_IMAGE = 53; // the same, into a sub-rectangle
    private static final int SEM_TEX_PARAM_I = 54;     // (target,pname,param) -> texture parameter of the bound texture
    private static final int SEM_TEX_PARAM_IV = 55;    // (target,pname,values) -> the same, from an array
    private static final int SEM_TEX_PARAM_I_DSA = 56; // (texture,pname,param) -> the same, texture named directly
    private static final int SEM_TEX_PARAM_IV_DSA = 57; // (texture,pname,values) -> the same, from an array
    private static final int SEM_MULT_MATRIX = 58;     // (matrix) -> multiply the active matrix of the mirror
    private static final int SEM_MATRIX_MODE = 59;     // (mode) -> which stack glMultMatrix* multiplies
    private static final int SEM_BIND_FRAMEBUFFER = 28; // (target,name) -> framebuffer binding record
    private static final int SEM_CLEAR = 29;          // (mask) -> pending clear of the owned frame image
    private static final int SEM_CLEAR_COLOR = 30;    // (r,g,b,a) -> the colour glClear will use
    private static final int SEM_CLEAR_DEPTH = 31;    // (d) -> the depth glClear will use
    private static final int SEM_BUFFER_SUBDATA = 32; // (target,offset,size,data) -> into the recorded storage
    private static final int SEM_LINE_WIDTH = 33;      // (w) -> recorded raster line width
    private static final int SEM_USE_PROGRAM = 34;     // (program) -> recorded current program
    private static final int SEM_NEW_LIST = 35;        // (list,mode) -> the list being compiled
    private static final int SEM_END_LIST = 36;        // () -> compilation finished
    private static final int SEM_CALL_LIST = 37;       // (list) -> recorded for the display-list census
    private static final int SEM_BIND_VERTEX_ARRAY = 38; // (vao) -> recorded vertex array binding
    private static final int SEM_TEXIMAGE_PIXELS = 39; // (target,level,internal,w,h,border,fmt,type,pixels) -> extent + RGBA8
    private static final int SEM_TEXSUBIMAGE = 40;     // (target,level,x,y,w,h,fmt,type,pixels) -> patch into a captured level
    private static final int SEM_COMPRESSED_TEXIMAGE = 41; // (target,level,internal,w,h,border,size,data) -> extent only
    private static final int SEM_PIXEL_STORE = 42;     // (pname,value) -> the unpack state upload conversion needs
    private static final int SEM_VERTEX_POINTER = 43;  // (size,type,stride,offset) -> how to read the bound array buffer
    private static final int SEM_DRAW_ARRAYS = 44;     // (mode,first,count) -> one array-buffer draw, drawn natively
    private static final int SEM_COMPILED_ARRAYS = -2;
    private static final Fn COMPILED_ARRAYS = new Fn("compiledClientArrays", R_VOID, new byte[0], SEM_COMPILED_ARRAYS);
    // Step 1 of the loading-screen unit: glBegin/glEnd leave the generic no-op table so the *site* that opens a
    // legacy immediate-mode run can be proven rather than counted. Forge's SplashProgress draws the loading screen
    // with glBegin(GL_QUADS) (SplashProgress.java:294/:353/:416) straight through GL11, which under the GL-less
    // window reached SEM_NOOP and drew nothing; the no-op census showed glVertex3f=152460/glBegin=38115/glEnd=38115
    // but a count is not a site. This step only names the callers; the vertex recording and the submit are the next
    // two steps, each landing on its own.
    private static final int SEM_BEGIN = 68;           // (mode) -> the site that opens an immediate-mode run
    private static final int SEM_END = 69;             // () -> the site that closes it
    private static final int SEM_READ_PIXELS = 70;     // (x,y,w,h,format,type,pixels) -> read the bound read framebuffer
    private static final int SEM_SCISSOR = 45;         // (x,y,w,h) -> the game's clip rectangle, for the group's scissor
    private static final int SEM_CULL_FACE = 60;       // arg0 = the face GL will cull
    private static final int SEM_FRONT_FACE = 61;      // arg0 = the winding GL calls front
    private static final int SEM_POLYGON_OFFSET = 62;  // (factor, units) as float bits -> the depth bias GL wants
    private static final int SEM_COLOR_MASK = 63;      // (r,g,b,a) -> which channels the draw may write (BH-10)
    private static final int SEM_BLEND_FUNC = 64;      // (src,dst) -> all four GL blend factors, as glBlendFunc does
    private static final int SEM_BLEND_FUNC_SEPARATE = 65; // (srcRgb,dstRgb,srcAlpha,dstAlpha) -> the four factors
    private static final int SEM_BLEND_EQUATION = 66;  // (mode) -> both blend equations
    private static final int SEM_BLEND_EQUATION_SEPARATE = 67; // (rgbMode, alphaMode) -> the two equations

    /** One function-table row: name, return type, arg types, semantics. */
    private static final class Fn {
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

    private static final List<Fn> TABLE = new ArrayList<>();
    private static final Map<String, Integer> IDS = new HashMap<>();

    private static Fn fn(String name, byte ret, int semantics, byte... args) {
        Fn fn = new Fn(name, ret, args, semantics);
        TABLE.add(fn);
        return fn;
    }

    static {
        // ---- names: every generator returns a fresh id; the same table's delete frees it for reporting only
        fn("glGenTextures", R_INT, SEM_GEN, A_INT, A_PTR);
        fn("glGenBuffers", R_INT, SEM_GEN, A_INT, A_PTR);
        fn("glGenRenderbuffers", R_INT, SEM_GEN, A_INT, A_PTR);
        fn("glGenFramebuffers", R_INT, SEM_GEN, A_INT, A_PTR);
        fn("glGenVertexArrays", R_INT, SEM_GEN, A_INT, A_PTR);
        fn("glGenSamplers", R_INT, SEM_GEN, A_INT, A_PTR);
        fn("glGenQueries", R_INT, SEM_GEN, A_INT, A_PTR);
        fn("glGenLists", R_INT, SEM_GEN, A_INT);
        fn("glCreateShader", R_INT, SEM_GEN, A_INT);
        fn("glCreateProgram", R_INT, SEM_GEN);
        fn("glGenSync", R_LONG, SEM_GEN);
        fn("glFenceSync", R_LONG, SEM_GEN, A_INT, A_INT);
        fn("glGenTransformFeedbacks", R_INT, SEM_GEN, A_INT, A_PTR);
        fn("glGenProgramPipelines", R_INT, SEM_GEN, A_INT, A_PTR);
        // ---- queries that shape what the game thinks it has
        fn("glGetString", R_PTR, SEM_GET_STRING, A_INT);
        fn("glGetStringi", R_PTR, SEM_GET_STRINGI, A_INT, A_INT);
        fn("glGetError", R_INT, SEM_GET_ERROR);
        fn("glIsEnabled", R_INT, SEM_IS_ENABLED, A_INT);   // GLboolean - byte, marshalled as int by the caller
        fn("glIsEnabledi", R_INT, SEM_IS_ENABLED, A_INT, A_INT);
        fn("glIsTexture", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsBuffer", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsFramebuffer", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsRenderbuffer", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsVertexArray", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsShader", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsProgram", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsQuery", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsSampler", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsList", R_INT, SEM_IS_OBJECT, A_INT);
        fn("glIsSync", R_INT, SEM_IS_OBJECT, A_LONG);
        fn("glGetIntegerv", R_VOID, SEM_GET_INTEGERV, A_INT, A_PTR);
        fn("glGetInteger64v", R_VOID, SEM_GET_INTEGER64V, A_INT, A_PTR);
        fn("glGetFloatv", R_VOID, SEM_GET_FLOATV, A_INT, A_PTR);
        fn("glGetDoublev", R_VOID, SEM_GET_FLOATV, A_INT, A_PTR);   // doubles read as floats is a known GL quirk - 4B each
        fn("glGetBooleanv", R_VOID, SEM_GET_BOOLEANV, A_INT, A_PTR);
        fn("glEnable", R_VOID, SEM_ENABLE, A_INT);
        // BH-08: the immediate path's cull state never reached the renderer because the face calls were no-ops and
        // nothing read the capability. Both are recorded here instead: the enable already lands in the enables map
        // with every other capability, and the face and winding get their own semantic case.
        fn("glCullFace", R_VOID, SEM_CULL_FACE, A_INT);
        fn("glFrontFace", R_VOID, SEM_FRONT_FACE, A_INT);
        fn("glPolygonOffset", R_VOID, SEM_POLYGON_OFFSET, A_FLOAT, A_FLOAT);
        fn("glDisable", R_VOID, SEM_DISABLE, A_INT);
        // BH-09: Celeritas' own UI clips with GL11.glScissor directly (VintageDrawContext.enableScissor), so the
        // rectangle must be recorded rather than dropped - it was in the NOOP table, which is why a scroll list could
        // not be clipped. The enable bit is not a second mirror: scissorTest() reads the same enables map that
        // glEnable/glDisable write.
        fn("glScissor", R_VOID, SEM_SCISSOR, A_INT, A_INT, A_INT, A_INT);
        fn("glEnablei", R_VOID, SEM_ENABLE, A_INT, A_INT);
        fn("glDisablei", R_VOID, SEM_DISABLE, A_INT, A_INT);
        // ---- deletion: retire names so glIs* answers go false (names are never reissued - GL does not require it)
        fn("glDeleteTextures", R_VOID, SEM_DELETE, A_INT, A_PTR);
        fn("glDeleteBuffers", R_VOID, SEM_DELETE, A_INT, A_PTR);
        fn("glDeleteRenderbuffers", R_VOID, SEM_DELETE, A_INT, A_PTR);
        fn("glDeleteFramebuffers", R_VOID, SEM_DELETE, A_INT, A_PTR);
        fn("glDeleteVertexArrays", R_VOID, SEM_DELETE, A_INT, A_PTR);
        fn("glDeleteSamplers", R_VOID, SEM_DELETE, A_INT, A_PTR);
        fn("glDeleteQueries", R_VOID, SEM_DELETE, A_INT, A_PTR);
        fn("glDeleteLists", R_VOID, SEM_DELETE, A_INT, A_INT);
        fn("glDeleteShader", R_VOID, SEM_DELETE, A_INT);
        fn("glDeleteProgram", R_VOID, SEM_DELETE, A_INT);
        fn("glDeleteSync", R_VOID, SEM_DELETE, A_LONG);
        // ---- return-valued queries: a 0/garbage return would break callers that read it
        fn("glCheckFramebufferStatus", R_INT, SEM_FBO_COMPLETE, A_INT);
        fn("glCheckNamedFramebufferStatus", R_INT, SEM_FBO_COMPLETE, A_INT, A_INT);
        fn("glClientWaitSync", R_INT, SEM_SIGNALED, A_LONG, A_INT, A_LONG);
        fn("glRenderMode", R_INT, SEM_ZERO, A_INT);
        fn("glGetUniformLocation", R_INT, SEM_ZERO, A_INT, A_PTR);
        fn("glGetAttribLocation", R_INT, SEM_ZERO, A_INT, A_PTR);
        fn("glGetUniformBlockIndex", R_INT, SEM_ZERO, A_INT, A_PTR);
        fn("glGetProgramResourceIndex", R_INT, SEM_ZERO, A_INT, A_INT, A_PTR);
        fn("glCreateShaderProgramv", R_INT, SEM_GEN, A_INT, A_INT, A_PTR);
        fn("glGetTexLevelParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        // The DSA forms the modern renderer path actually calls: without these every size query answers 0, so
        // announcements are refused as "0x0" and mirrors are built 1x1 before the upload resizes them.
        fn("glGetTextureLevelParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        fn("glGetTextureLevelParameterfv", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        fn("glGetTextureParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetTextureParameterfv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetNamedFramebufferAttachmentParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        fn("glGetFramebufferAttachmentParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        fn("glGetTexLevelParameterfv", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        fn("glGetShaderiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetProgramiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetShaderInfoLog", R_VOID, SEM_INFO_LOG, A_INT, A_INT, A_PTR, A_PTR);
        fn("glGetProgramInfoLog", R_VOID, SEM_INFO_LOG, A_INT, A_INT, A_PTR, A_PTR);
        fn("glGetBufferParameteri64v", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetBufferParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetSynciv", R_VOID, SEM_STATUS, A_LONG, A_INT, A_INT, A_PTR, A_PTR);
        fn("glGetFramebufferParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetFramebufferAttachmentParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        fn("glGetRenderbufferParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetVertexAttribiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetVertexAttribPointerv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetUniformfv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetUniformiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetActiveUniform", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR, A_PTR, A_PTR, A_PTR);
        fn("glGetActiveAttrib", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR, A_PTR, A_PTR, A_PTR);
        fn("glGetActiveUniformsiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR, A_INT, A_PTR);
        fn("glGetActiveUniformBlockiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        fn("glGetProgramResourceiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_INT, A_INT, A_PTR, A_INT, A_PTR);
        fn("glGetProgramResourceName", R_VOID, SEM_INFO_LOG, A_INT, A_INT, A_INT, A_INT, A_PTR, A_PTR);
        fn("glGetShaderSource", R_VOID, SEM_INFO_LOG, A_INT, A_INT, A_PTR, A_PTR);
        fn("glGetAttachedShaders", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR, A_PTR);
        fn("glGetPointerv", R_VOID, SEM_STATUS, A_INT, A_PTR);
        fn("glGetBufferPointerv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetQueryObjectiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetQueryObjectuiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetQueryObjecti64v", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetQueryObjectui64v", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        // ---- buffer storage: glBufferData allocates, glMapBuffer returns the storage address
        fn("glBufferData", R_VOID, SEM_BUFFER_DATA, A_INT, A_LONG, A_PTR, A_INT);
        fn("glNamedBufferData", R_VOID, SEM_BUFFER_DATA, A_INT, A_LONG, A_PTR, A_INT);
        fn("glBufferStorage", R_VOID, SEM_BUFFER_DATA, A_INT, A_LONG, A_PTR, A_INT);
        fn("glNamedBufferStorage", R_VOID, SEM_BUFFER_DATA, A_INT, A_LONG, A_PTR, A_INT);
        fn("glMapBuffer", R_PTR, SEM_MAP, A_INT, A_INT);
        fn("glMapNamedBuffer", R_PTR, SEM_MAP, A_INT, A_INT);
        fn("glMapBufferRange", R_PTR, SEM_MAP, A_INT, A_LONG, A_LONG, A_INT);
        fn("glMapNamedBufferRange", R_PTR, SEM_MAP, A_INT, A_LONG, A_LONG, A_INT);
        fn("glUnmapBuffer", R_INT, SEM_UNMAP, A_INT);
        fn("glUnmapNamedBuffer", R_INT, SEM_UNMAP, A_INT);
        fn("glBindBuffer", R_VOID, SEM_BIND_BUFFER, A_INT, A_INT);
        // ---- texture bindings/images: size tracking feeds glGetTexLevelParameter (FML probes the max texture
        // size by uploading proxy images) and the pixel capture the Vulkan mirrors are built from.
        fn("glBindTexture", R_VOID, SEM_BIND_TEXTURE, A_INT, A_INT);
        fn("glActiveTexture", R_VOID, SEM_ACTIVE_TEXTURE, A_INT);
        fn("glTexImage1D", R_VOID, SEM_TEXIMAGE_PIXELS, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        fn("glTexImage2D", R_VOID, SEM_TEXIMAGE_PIXELS, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        // glTexImage3D carries depth between height and border: (target,level,internal,w,h,depth,border,fmt,type,pixels)
        fn("glTexImage3D", R_VOID, SEM_TEXIMAGE_PIXELS, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        fn("glTexStorage2D", R_VOID, SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT);
        fn("glTexStorage3D", R_VOID, SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        fn("glCompressedTexImage2D", R_VOID, SEM_COMPRESSED_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        fn("glTexSubImage2D", R_VOID, SEM_TEXSUBIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        fn("glPixelStorei", R_VOID, SEM_PIXEL_STORE, A_INT, A_INT);
        fn("glViewport", R_VOID, SEM_VIEWPORT, A_INT, A_INT, A_INT, A_INT);
        // Texture-environment state. The stub used to no-op all of it, so the two queries the renderer asks - the
        // combine mode and the environment colour, both on the lightmap unit - returned zero and the hurt flash was
        // invisible to the instrument as well as to the frame (measured by entity-models: the reference shifts the
        // zombie head from (78.9,123.7,87.7) to (117.3,105.0,79.4) and ours moves by at most 1.8).
        fn("glFogi", R_VOID, SEM_FOG_I, A_INT, A_INT);
        fn("glFogf", R_VOID, SEM_FOG_F, A_INT, A_FLOAT);
        fn("glFogfv", R_VOID, SEM_FOG_FV, A_INT, A_PTR);
        fn("glFogiv", R_VOID, SEM_FOG_IV, A_INT, A_PTR);
        fn("glTexEnvi", R_VOID, SEM_TEX_ENV_I, A_INT, A_INT, A_INT);
        fn("glTexEnviv", R_VOID, SEM_TEX_ENV_I, A_INT, A_INT, A_PTR);
        fn("glTexEnvf", R_VOID, SEM_TEX_ENV_F, A_INT, A_INT, A_FLOAT);
        fn("glTexEnvfv", R_VOID, SEM_TEX_ENV_FV, A_INT, A_INT, A_PTR);
        // LWJGL2's GlStateManager.glTexEnv(int,int,FloatBuffer) arrives under the bare name, which is the fv variant:
        // RenderLivingBase.setBrightness writes the hurt/team env colour through exactly this call, and while it sat
        // in the noop list texEnvColor stayed zero - the combine fired but the mix factor was black at 0.
        fn("glTexEnv", R_VOID, SEM_TEX_ENV_FV, A_INT, A_INT, A_PTR);
        fn("glGetTexEnvi", R_VOID, SEM_GET_TEX_ENV_I, A_INT, A_INT, A_PTR);
        fn("glGetTexEnviv", R_VOID, SEM_GET_TEX_ENV_I, A_INT, A_INT, A_PTR);
        fn("glGetTexEnvfv", R_VOID, SEM_GET_TEX_ENV_FV, A_INT, A_INT, A_PTR);
        // Texture parameters: the filter and wrap state GL keeps per texture object. These used to be in the no-op
        // list, so every texture answered the same hardcoded value and the real one was gone - measured as the
        // options background sampling CLAMP_TO_EDGE where vanilla tiles it with REPEAT (GuiOptions.drawBackground
        // and Cleanroom's patched GuiSlot.drawContainerBackground both draw it with UVs up to height/32), and the
        // sky faces likewise. Recorded per texture *name* rather than per binding because the queries that need it
        // (the mirror's wrap read, the DSA glGetTextureParameter*) name the texture, not the unit.
        fn("glTexParameteri", R_VOID, SEM_TEX_PARAM_I, A_INT, A_INT, A_INT);
        fn("glTexParameterf", R_VOID, SEM_TEX_PARAM_I, A_INT, A_INT, A_FLOAT);
        fn("glTexParameteriv", R_VOID, SEM_TEX_PARAM_IV, A_INT, A_INT, A_PTR);
        fn("glTexParameterfv", R_VOID, SEM_TEX_PARAM_IV, A_INT, A_INT, A_PTR);
        fn("glTexParameterIiv", R_VOID, SEM_TEX_PARAM_IV, A_INT, A_INT, A_PTR);
        fn("glTexParameterIuiv", R_VOID, SEM_TEX_PARAM_IV, A_INT, A_INT, A_PTR);
        fn("glGetTexParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetTexParameterfv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetTexParameterIiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetTexParameterIuiv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        // The DSA forms name the texture directly: the modern mirror path asks with glGetTextureParameteri (LWJGL
        // answers that over glGetTextureParameteriv) and sets with glTextureParameteri.
        fn("glTextureParameteri", R_VOID, SEM_TEX_PARAM_I_DSA, A_INT, A_INT, A_INT);
        fn("glTextureParameterf", R_VOID, SEM_TEX_PARAM_I_DSA, A_INT, A_INT, A_FLOAT);
        fn("glTextureParameteriv", R_VOID, SEM_TEX_PARAM_IV_DSA, A_INT, A_INT, A_PTR);
        fn("glTextureParameterfv", R_VOID, SEM_TEX_PARAM_IV_DSA, A_INT, A_INT, A_PTR);
        fn("glTextureParameterIiv", R_VOID, SEM_TEX_PARAM_IV_DSA, A_INT, A_INT, A_PTR);
        fn("glTextureParameterIuiv", R_VOID, SEM_TEX_PARAM_IV_DSA, A_INT, A_INT, A_PTR);
        // Matrix multiplication that never goes through GlStateManager. Forge's item transform is the measured case:
        // ForgeHooksClient.handleCameraTransforms -> multiplyCurrentGlMatrix -> GL11.glMultMatrix(matrixBuf)
        // (ForgeHooksClient.java:425-455), so the item's 30/225-degree rotation reached the stub's no-op list while
        // every GlStateManager-routed matrix call reached the mirror - the mirror kept the GUI ortho as the group's
        // modelview and all six faces of a block projected onto one square (the user's flat slot icon). The same entry
        // point is what the GLU perspective helper uses: org.lwjglx.util.glu.Project.gluPerspective calls
        // GL11.glMultMatrixf at two sites (javap of lwjglxx-1.1.22), so this hook is also the projection channel's
        // missing link - glMatrixMode is recorded here so the census says which stack the multiply landed on.
        fn("glMultMatrixf", R_VOID, SEM_MULT_MATRIX, A_PTR);
        fn("glMultMatrixd", R_VOID, SEM_MULT_MATRIX, A_PTR);
        fn("glMatrixMode", R_VOID, SEM_MATRIX_MODE, A_INT);
        // Copy-from-framebuffer. The main menu's panorama is built with these: renderSkybox draws the six 256x256
        // cube faces into the window and copies each into the skybox texture, and the blur passes copy their own
        // targets. Registered rather than left in the no-op list so the request is named and counted: under GL-less
        // there is no GL framebuffer to read the pixels from, so what is missing is a readback of the renderer's own
        // image for the bound read framebuffer, which is a different component's resource.
        fn("glCopyTexImage2D", R_VOID, SEM_COPY_TEX_IMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        fn("glCopyTexSubImage2D", R_VOID, SEM_COPY_TEX_SUB_IMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        // glReadPixels is the same question the copies answer, asked into a client buffer: the bound read framebuffer's
        // rectangle from the image the renderer owns. It used to sit in the no-op list, which left every caller's
        // buffer zeroed - the fixture's format-2 readback measured "all mismatch" and ScreenShotHelper's screenshots
        // came out blank - so it is registered and served now.
        fn("glReadPixels", R_VOID, SEM_READ_PIXELS, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        fn("glBindFramebuffer", R_VOID, SEM_BIND_FRAMEBUFFER, A_INT, A_INT);
        fn("glBindFramebufferEXT", R_VOID, SEM_BIND_FRAMEBUFFER, A_INT, A_INT);
        fn("glBindFramebufferARB", R_VOID, SEM_BIND_FRAMEBUFFER, A_INT, A_INT);
        // ---- clears: with no GL framebuffer to clear, the request is recorded for the owned frame image. Without
        // this the image's depth holds undefined values for the whole session and every depth-tested draw fails.
        fn("glClear", R_VOID, SEM_CLEAR, A_INT);
        fn("glClearColor", R_VOID, SEM_CLEAR_COLOR, A_FLOAT, A_FLOAT, A_FLOAT, A_FLOAT);
        fn("glClearDepth", R_VOID, SEM_CLEAR_DEPTH, A_DOUBLE);
        fn("glClearDepthf", R_VOID, SEM_CLEAR_DEPTH, A_FLOAT);
        // ---- state the getters answer from: recorded rather than left at the no-op default
        fn("glLineWidth", R_VOID, SEM_LINE_WIDTH, A_FLOAT);
        fn("glUseProgram", R_VOID, SEM_USE_PROGRAM, A_INT);
        fn("glNewList", R_VOID, SEM_NEW_LIST, A_INT, A_INT);
        fn("glEndList", R_VOID, SEM_END_LIST);
        fn("glCallList", R_VOID, SEM_CALL_LIST, A_INT);
        fn("glBindVertexArray", R_VOID, SEM_BIND_VERTEX_ARRAY, A_INT);
        fn("glBufferSubData", R_VOID, SEM_BUFFER_SUBDATA, A_INT, A_LONG, A_LONG, A_PTR);
        // ---- client-array drawing: the pointer state that says how to read the bound array buffer's bytes, and the
        // draw that uses it. This is the VBO path RenderGlobal.renderSky takes (`skyVBO.drawArrays(7)` ->
        // VertexBuffer.drawArrays -> GlStateManager.glDrawArrays -> GL11.glDrawArrays), and it is the one entry point
        // here that produces geometry rather than state: answering it with a no-op is a hole in the picture, not a
        // state detail - measured as a GL-less world frame with no sky dome, no stars and no horizon plane while no
        // counter mentioned them.
        //
        // glEnableClientState/glDisableClientState are the same capability bits glEnable sets (GL_VERTEX_ARRAY is
        // 32884), so they share those semantics: glIsEnabled answers from the same record, as GL does.
        fn("glEnableClientState", R_VOID, SEM_ENABLE, A_INT);
        fn("glDisableClientState", R_VOID, SEM_DISABLE, A_INT);
        fn("glVertexPointer", R_VOID, SEM_VERTEX_POINTER, A_INT, A_INT, A_INT, A_PTR);
        fn("glDrawArrays", R_VOID, SEM_DRAW_ARRAYS, A_INT, A_INT, A_INT);
        fn("glBegin", R_VOID, SEM_BEGIN, A_INT);
        fn("glEnd", R_VOID, SEM_END);
        // ---- the blend and colour-write state (BH-07, BH-10): the two the games' own wrappers reach through
        // OpenGlHelper rather than through a GlStateManager field, which is why a mixin hook could not see them.
        // Read from the original: `GlStateManager.blendFunc(int,int)` calls `GL11.glBlendFunc` directly, while both
        // `tryBlendFuncSeparate` overloads delegate to `OpenGlHelper.glBlendFunc`, which with OpenGL14 present (the
        // stub reports GL 4.6, so it is) calls `GL14.glBlendFuncSeparate`. The mixin hooks the enum overload only, so
        // the int overload - the one JsonBlendingMode and every Forge caller with plain ints uses - reached the
        // no-op table and the mirror kept the last enum call's factors. Recording here catches every caller of every
        // shape, including raw GL, for the same reason the cull capability is recorded here (BH-08).
        fn("glBlendFunc", R_VOID, SEM_BLEND_FUNC, A_INT, A_INT);
        fn("glBlendFuncSeparate", R_VOID, SEM_BLEND_FUNC_SEPARATE, A_INT, A_INT, A_INT, A_INT);
        fn("glBlendEquation", R_VOID, SEM_BLEND_EQUATION, A_INT);
        fn("glBlendEquationSeparate", R_VOID, SEM_BLEND_EQUATION_SEPARATE, A_INT, A_INT);
        // BH-10: the Fancy-cloud depth-only pass reaches this through GlStateManager.colorMask, which is itself
        // cached and calls GL11.glColorMask only when the mask changes - so the stub is the one place that sees the
        // change that matters (false,false,false,false at RenderGlobal:1655).
        fn("glColorMask", R_VOID, SEM_COLOR_MASK, A_INT, A_INT, A_INT, A_INT);
        // ---- DSA create-functions: same out-array shape as the glGen* family
        fn("glCreateTextures", R_VOID, SEM_GEN, A_INT, A_INT, A_PTR);
        fn("glCreateBuffers", R_VOID, SEM_GEN, A_INT, A_PTR);
        fn("glCreateFramebuffers", R_VOID, SEM_GEN, A_INT, A_PTR);
        fn("glCreateRenderbuffers", R_VOID, SEM_GEN, A_INT, A_PTR);
        fn("glCreateVertexArrays", R_VOID, SEM_GEN, A_INT, A_PTR);
        fn("glCreateSamplers", R_VOID, SEM_GEN, A_INT, A_PTR);
        fn("glCreateQueries", R_VOID, SEM_GEN, A_INT, A_INT, A_PTR);
        fn("glCreateTransformFeedbacks", R_VOID, SEM_GEN, A_INT, A_PTR);
        fn("glCreateProgramPipelines", R_VOID, SEM_GEN, A_INT, A_PTR);
        // ---- state-set no-ops: the fixed-function and raster state the Vulkan side mirrors or owns
        String[] noops = {
                "glBindRenderbuffer",
                "glBindSampler", "glBindBufferBase", "glBindBufferRange",
                "glBlendFuncSeparatei", "glBlendEquationi", "glBlendEquationSeparatei",
                "glBlendColor", "glColorMaski", "glDepthMask", "glDepthFunc", "glDepthRange",
                "glClearBufferfv", "glClearBufferiv",
                "glClearBufferuiv", "glStencilFunc", "glStencilFuncSeparate", "glStencilOp", "glStencilOpSeparate",
                "glStencilMask", "glStencilMaskSeparate", "glPolygonMode",
                "glPointSize", "glPrimitiveRestartIndex", "glProvokingVertex",                "glSampleCoverage", "glSampleMaski", "glLogicOp", "glHint",
                "glPixelStoref", "glReadBuffer", "glDrawBuffer", "glDrawBuffers", "glClampColor",
                "glColorMaterial", "glMaterialf", "glMaterialfv", "glMateriali", "glLightf", "glLightfv",
                "glLighti", "glLightiv", "glLightModelf", "glLightModelfv", "glLightModeli", "glLightModeliv",
                "glShadeModel", "glFogf", "glFogfv", "glFogi", "glFogiv", "glTexEnvi", "glTexEnvf", "glTexEnviv",
                "glTexEnvfv", "glTexGeni", "glTexGenf", "glTexGeniv", "glTexGenfv", "glAlphaFunc", "glNormal3f",
                "glNormal3b", "glNormal3fv", "glColor4f", "glColor3f", "glColor4ub", "glColor3ub", "glSecondaryColor3f",
                "glTexCoord2f", "glMultiTexCoord2f", "glVertex3f",
                "glLoadIdentity", "glLoadMatrixf", "glLoadMatrixd", "glPushMatrix",
                "glPopMatrix", "glOrtho", "glFrustum", "glTranslatef", "glTranslated", "glRotatef", "glRotated",
                "glScalef", "glScaled", "glGenerateMipmap", "glSamplerParameteri",
                "glSamplerParameterf", "glSamplerParameteriv", "glSamplerParameterIiv", "glSamplerParameterIuiv",
                "glAttachShader", "glDetachShader", "glBindAttribLocation", "glBindFragDataLocation",
                "glLinkProgram", "glValidateProgram", "glShaderSource", "glCompileShader", "glUniform1i",
                "glUniform1f", "glUniform2f", "glUniform3f", "glUniform4f", "glUniform1iv", "glUniform2iv",
                "glUniform3iv", "glUniform4iv", "glUniform1fv", "glUniform2fv", "glUniform3fv", "glUniform4fv",
                "glUniformMatrix2fv", "glUniformMatrix3fv", "glUniformMatrix4fv", "glEnableVertexAttribArray",
                "glDisableVertexAttribArray", "glVertexAttribPointer", "glVertexAttribIPointer",
                "glVertexAttribLPointer", "glVertexAttribDivisor", "glVertexAttrib1f", "glVertexAttrib2f",
                "glVertexAttrib3f", "glVertexAttrib4f", "glVertexAttrib4Nub", "glDrawElements",
                "glDrawRangeElements", "glDrawElementsInstanced", "glDrawArraysInstanced", "glMultiDrawArrays",
                "glMultiDrawElements", "glDrawElementsBaseVertex", "glBeginQuery", "glEndQuery", "glBeginQueryIndexed",
                "glEndQueryIndexed", "glClientWaitSync", "glWaitSync", "glFlush", "glFinish", "glPushAttrib",
                "glPopAttrib", "glPushClientAttrib", "glPopClientAttrib", "glCallLists",
                "glListBase", "glRenderMode", "glSelectBuffer", "glFeedbackBuffer", "glInitNames",
                "glPushName", "glPopName", "glLoadName", "glRasterPos2f", "glRasterPos3f", "glBitmap",
                "glCopyPixels", "glDrawPixels",
                "glBlitFramebuffer", "glFramebufferTexture2D", "glFramebufferTexture", "glFramebufferTexture1D",
                "glFramebufferTexture3D", "glFramebufferTextureLayer", "glFramebufferRenderbuffer",
                "glRenderbufferStorage", "glRenderbufferStorageMultisample",
                "glTexSubImage1D", "glTexSubImage3D",
                "glCompressedTexSubImage2D", "glGetTexImage",
                "glGetCompressedTexImage", "glMapBuffer", "glMapBufferRange",
                "glCopyBufferSubData", "glFlushMappedBufferRange", "glGetBufferSubData",
                "glTexCoordPointer", "glColorPointer", "glNormalPointer", "glIndexPointer", "glEdgeFlagPointer",
                "glSecondaryColorPointer", "glFogCoordPointer", "glInterleavedArrays", "glArrayElement",
                "glDrawBuffer", "glRects", "glClipPlane", "glSecondaryColor3fv", "glColor4fv",
                "glDepthBoundsEXT", "glMultiDrawArraysIndirect", "glMultiDrawElementsIndirect", "glDrawArraysIndirect",
                "glDrawElementsIndirect", "glDispatchCompute", "glMemoryBarrier", "glTextureBarrier",
                "glFramebufferParameteri", "glNamedFramebufferTexture",
                "glNamedFramebufferRenderbuffer", "glNamedRenderbufferStorage",
                "glTextureImage2DEXT",
                "glTextureSubImage2D", "glNamedTexImage2DEXT", "glNamedCopyTexSubImage2DEXT", "glTextureStorage2D",
                "glGenerateTextureMipmap", "glBindTextureUnit",
                "glBindImageTexture", "glTexBuffer", "glTexBufferRange",
                "glUniformBlockBinding",
                "glUseProgramStages", "glActiveShaderProgram", "glProgramUniform1i", "glProgramUniform1f",
                "glProgramUniform2f", "glProgramUniform3f", "glProgramUniform4f", "glProgramUniform1iv",
                "glProgramUniformMatrix4fv"
        };
        for (String name : noops) {
            // Rough signature for the table: every arg marshals as int/pointer uniformly - the noop discards them,
            // so an arg count is all the trampoline needs.
            fn(name, R_VOID, SEM_NOOP, new byte[] { A_PTR, A_PTR, A_PTR, A_PTR, A_PTR, A_PTR, A_PTR, A_PTR });
        }
        for (int i = 0; i < TABLE.size(); i++) {
            IDS.putIfAbsent(TABLE.get(i).name, Integer.valueOf(i));
        }
    }

    // ---- runtime state ---------------------------------------------------------------

    private final Map<String, Callback> trampolines = new HashMap<>();
    private final java.util.concurrent.atomic.AtomicLong nextSync = new java.util.concurrent.atomic.AtomicLong(1);
    /** The serial every filed level write takes its number from; see {@link TexCapture#serial}. */
    private final java.util.concurrent.atomic.AtomicLong texUploadSerial =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.Set<Long> liveSync = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final java.util.Set<Integer> liveNames = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    /**
     * The GL state tables are open-addressed primitive maps, not ConcurrentHashMaps: every read and write happens on
     * the render thread by GL contract (the stub's callbacks are invoked by the backend there, never by a worker),
     * so the concurrency the CHMs bought was unused - while their per-call cost was real: a {@code Long.valueOf} box
     * plus the hash probe on every bind and every texture-parameter lookup, ~4.6% of the unbounded leg's warm
     * samples between {@code ConcurrentHashMap.get}/{@code putVal} and {@code Integer.valueOf}.
     */
    // The two hottest maps the GL call path owned were hash lookups: every glEnable/glDisable/glIsEnabled took an
    // Int2Object get/put through a Boolean box, and every buffer bind did the same through Int2Int. Both key spaces
    // are capability/target constants - dense, small, and bounded well under 0x10000 - so they are flat arrays now:
    // one indexed load or store per call, no hashing, no boxing, no tree-node buckets (the steady-state JFR showed
    // HashMap$TreeNode.getTreeNode and Long2ObjectOpenHashMap.get among the top frames). An index of 0 is "never
    // set", which is the same answer the maps gave for absent keys: GL treats an unqueried capability as disabled
    // and an unbound target as name 0.
    private static final int FLAT_MAP_SIZE = 0x10000;
    private final byte[] enables = new byte[FLAT_MAP_SIZE];
    // A false value may be an observed disable or simply an untouched provider default. Native replacements
    // can bypass callbacks, so the latter must never erase state captured through Minecraft's API.
    private final boolean[] capabilityObserved = new boolean[FLAT_MAP_SIZE];
    private final int[] bufferBindings = new int[FLAT_MAP_SIZE];
    /** Bound texture name per (active unit, target). The game uses one unit; the key keeps the option open. A read
     * of an unbound (unit,target) returns -1 - never a valid GL name. */
    private final it.unimi.dsi.fastutil.longs.Long2IntMap textureBindings =
            new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    /**
     * First caller each texture name was bound through, either route - a bound-but-never-uploaded name cannot have
     * its producer reconstructed from the upload records (there are none), which is exactly the state the "no mirror
     * for this GL id" refusal reports. Bounded so a mod that churns names cannot grow it; texture name 506 in the
     * E2E pack was bound and never uploaded, and without this there was nothing to name the caller.
     */
    private final java.util.Map<Integer, String> firstBindSites = new java.util.HashMap<Integer, String>();
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
    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<int[]> texLevels =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    /**
     * Captured uploads in RGBA8, keyed like {@link #texLevels}. A full {@code glTexImage*} publishes a new record, so
     * a reader sees the old image or the new one and never a half-written one; {@code glTexSubImage2D} patches rows
     * of the record that is already there.
     */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<TexCapture> texCaptures =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    /** The capture census {@link #textureCaptureSummary()} reports: full images, patches and refused uploads. */
    private final java.util.concurrent.atomic.AtomicInteger fullCaptures =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger subCaptures =
            new java.util.concurrent.atomic.AtomicInteger();
    /** Levels that were allocated with no data - a zero-filled surface waiting for the patches that fill it. */
    private final java.util.concurrent.atomic.AtomicInteger undefinedExtents =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger unconvertedUploads =
            new java.util.concurrent.atomic.AtomicInteger();
    /**
     * Patches whose converted bytes were row-for-row identical to the captured surface. Each is an upload the
     * caller asked for that changed nothing - the lightmap's per-frame DynamicTexture update is the regular one -
     * so skipping the serial bump keeps the descriptor the GPU side already holds instead of forcing a re-supply
     * of identical pixels.
     */
    private final java.util.concurrent.atomic.AtomicInteger identicalPatches =
            new java.util.concurrent.atomic.AtomicInteger();
    /**
     * The unpack state {@code glPixelStorei} set. Conversion reads rows where GL would read them: assuming tight rows
     * while the game asked for a row length or a row skip would store a sheared image instead of a missing one.
     */
    private volatile int unpackAlignment = 4;
    private volatile int unpackRowLength;
    private volatile int unpackSkipPixels, unpackSkipRows;
    /**
     * The size GL was last told a buffer holds, per buffer name.
     * <p>
     * The storage below is reused when it is already large enough, so its capacity is not the buffer's size: after
     * {@code glBufferData(size=16)} then {@code glBufferData(size=4)} the capacity is still 16, and a range check against
     * it accepted a mapping of bytes 8..12 that the 4-byte buffer does not have (#387). Everything that asks "how big is
     * this buffer" reads this map; the capacity only decides when the allocation grows.
     */
    private final java.util.Map<Integer, Integer> bufferLogicalSize = new java.util.HashMap<Integer, Integer>();

    private final java.util.Map<Integer, ByteBuffer> bufferStore =
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    private final java.util.List<Callback> ownedTrampolines = new ArrayList<>();
    private long getStringAddress;
    private ByteBuffer getStringBuffer;
    private volatile int viewportX, viewportY;
    private volatile int viewportW = 1280, viewportH = 720;
    /** GL_DRAW_FRAMEBUFFER / GL_READ_FRAMEBUFFER bindings; GL_FRAMEBUFFER sets both. */
    private volatile int drawFbo, readFbo;
    /** The colour and depth glClear will use, and the buffers it was asked to clear and nobody consumed yet. */
    private volatile float clearRed, clearGreen, clearBlue = 0.0f, clearAlpha = 1.0f;
    private volatile double clearDepth = 1.0;
    /** The GL framebuffer name bound when the pending clear was issued; 0 is the default framebuffer. */
    private volatile int pendingClearDrawFbo;
    /** Whether the backend's routing considered the bound framebuffer the main one when the clear was issued. */
    private volatile boolean pendingClearIsMain = true;

    /** Clears seen for the framebuffer the game calls its main one, and for every other target. */
    private int mainClears;
    private int foreignClears;
    private float lastForeignClearRed;
    private float lastForeignClearGreen;
    private float lastForeignClearBlue;
    private float lastMainClearRed;
    /** The alpha and depth of the last main-framebuffer clear, as the game set them when it issued that clear. */
    private float lastMainClearAlpha;
    private double lastMainClearDepth = 1.0D;
    private boolean mainClearValuesKnown;
    private float lastMainClearGreen;
    private float lastMainClearBlue;

    /** {@return the main/foreign clear census and the last main clear colour}, for the verification line. */
    public static String clearCensus() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "main=none foreign=none colour=none";
        }
        return "main=" + stub.mainClears + "/" + stub.lastMainClearRed + "," + stub.lastMainClearGreen + ","
                + stub.lastMainClearBlue + " foreign=" + stub.foreignClears + "/" + stub.lastForeignClearRed + ","
                + stub.lastForeignClearGreen + "," + stub.lastForeignClearBlue;
    }
    /** Set by the backend after each bind, so a clear is classified by routing rather than by a raw GL name. */
    private volatile boolean drawFramebufferIsMain = true;

    private final java.util.concurrent.atomic.AtomicInteger pendingClears =
            new java.util.concurrent.atomic.AtomicInteger();
    /** Fixed-pipeline state the getters answer from, at the values a fresh GL context starts them at. */
    private volatile float currentRed = 1.0f, currentGreen = 1.0f, currentBlue = 1.0f, currentAlpha = 1.0f;
    private volatile float lineWidth = 1.0f;
    /** The program glUseProgram bound, the display list being compiled, and the bound vertex array. */
    private volatile int currentProgram, compilingList, boundVertexArray, lastCalledList;
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
                // GL's default GL_UNPACK_ALIGNMENT is 4: each row starts on a four-byte boundary.
                bytes = (long) ((rowBytes + 3) & ~3) * height;
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
                bytes = (long) ((rowBytes + 3) & ~3) * height;
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
    private final Map<Integer, List<NativeGeometryBuffer>> displayListGeometry = new HashMap<>();
    private List<NativeGeometryBuffer> openListGeometry;

    private void releaseListPayloads(Integer list) {
        List<NativeGeometryBuffer> geometry = this.displayListGeometry.remove(list);
        if (geometry != null) {
            for (NativeGeometryBuffer buffer : geometry) buffer.close();
            this.listPayloadsReleased += geometry.size();
        }
        List<java.nio.ByteBuffer> payloads = this.displayListPayloads.remove(list);
        if (payloads == null) {
            return;
        }
        for (int i = 0; i < payloads.size(); i++) {
            org.lwjgl.system.MemoryUtil.memFree(payloads.get(i));
        }
        this.listPayloadsReleased += payloads.size();
    }

    /** The mode glNewList was given for {@link #compilingList}: GL_COMPILE or GL_COMPILE_AND_EXECUTE. */
    private volatile int compilingListMode;
    /**
     * The list {@code glCallList} is replaying right now, 0 outside a replay.
     *
     * <p>Separate from {@link #lastCalledList}, which stays at the last name for diagnostics. This one is the
     * answer to "what is being drawn", which {@code GL_LIST_INDEX} cannot give: the query names the list being
     * compiled and stays 0 while a list is executed.
     */
    private volatile int executingList;
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

    /** Owned payloads per compiled list name, released on replacement or deletion. */
    private final Map<Integer, List<java.nio.ByteBuffer>> displayListPayloads = new HashMap<>();

    /** Compiled commands whose pointer payload was copied into list-owned memory. */
    private long listPayloadsOwned;

    /** Compiled commands whose pointer payload could not be sized and was left pointing at the caller's buffer. */
    private long listPayloadsUnsized;

    /** Payload buffers released with their list. */
    private long listPayloadsReleased;

    /** Display-list names retired by glDeleteLists. */
    private long displayListsDeleted;

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
    private final Map<Integer, List<Object[]>> displayLists = new java.util.concurrent.ConcurrentHashMap<>();
    /** Replays and recorded calls per display-list id, for the sky lists' census (RenderGlobal's two of them). */
    private final int[] listReplayCounts = new int[512];
    private final long[] listReplayTotals = new long[512];

    /** {@return the most-replayed display lists and their recorded call counts}, for the verification line. */
    public static String listCensus() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        Integer[] ids = new Integer[stub.listReplayCounts.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Integer.valueOf(i);
        }
        java.util.Arrays.sort(ids, (a, b) -> Integer.compare(stub.listReplayCounts[b.intValue()],
                stub.listReplayCounts[a.intValue()]));
        for (int i = 0; i < 10 && i < ids.length; i++) {
            int id = ids[i].intValue();
            if (stub.listReplayCounts[id] == 0) {
                break;
            }
            text.append(text.length() == 0 ? "" : ",").append(id).append(":x").append(stub.listReplayCounts[id])
                    .append("/calls=").append(stub.listReplayTotals[id]);
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    /** How deep a replay currently is. glCallList may appear inside a list; the spec puts the limit at 64 or more. */
    private int replayDepth;
    private static final int MAX_LIST_NESTING = 64;
    private volatile long displayListsCompiled, displayListsReplayed, displayListsUnresolved;
    /**
     * The client-array pointer state: {@code glVertexPointer(size, type, stride, pointer)}. GL keeps exactly one of
     * these, and it is the only thing that says how the bound array buffer's bytes are to be read - the bytes alone
     * are anonymous. Recorded rather than no-opped for the same reason the buffer storage is mirrored: a draw that
     * arrives without it cannot be decoded, and guessing a layout would submit the wrong bytes rather than none.
     */
    private volatile int vertexPointerSize, vertexPointerType, vertexPointerStride;
    private volatile long vertexPointerOffset;
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
    private int texColorRepacks, texColorRepackDumps;

    /** The installed stub - one per process; GL capabilities are process-global so this is a singleton. */
    private static CeltiumGlStub installed;

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
            for (java.lang.reflect.Field field : capabilitiesClass.getFields()) {
                String name = field.getName();
                if (name.startsWith("GL_") || name.startsWith("OpenGL")) {
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
            celtium$printAtExit("CELTIUM_GLLESS LWJGL3 capabilities installed first from the stub provider: extensions="
                    + extensions.size() + " thread=" + Thread.currentThread().getName());
        } catch (Throwable error) {
            celtium$printAtExit("CELTIUM_GLLESS first capabilities install failed: " + error);
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
                if (DRAWARRAY_CALLS.get() > 0L) {
                    celtium$printAtExit("CELTIUM_DRAWARRAYS " + drawArraySummary());
                }
                if (installed != null && (installed.texParamCalls.get() > 0 || installed.texParamQueries.get() > 0)) {
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
                // matters for the reported defects - it is the count of glCallList calls that found a list and
                // drew it, and `unresolved` is the count that found nothing, which the spec says is ignored.
                if (installed != null && (installed.displayListsCompiled > 0 || installed.displayListsReplayed > 0
                        || installed.displayListsUnresolved > 0)) {
                    celtium$printAtExit("CELTIUM_DISPLAY_LISTS compiled=" + installed.displayListsCompiled
                            + " replayed=" + installed.displayListsReplayed
                            + " unresolved=" + installed.displayListsUnresolved
                            + " held=" + installed.displayLists.size());
                }
            }, "celtium-noop-census"));
        }
        return installed;
    }

    /**
     * Whether this stub answers GL calls in this process. When it does there is no GL device identity to read -
     * the renderer's strings are ours, not a driver's.
     */
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
            stub.liveNames.add(scalar + i);
        }
        return scalar;
    }

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
        return stub == null ? 0 : stub.executingList;
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
        return stub == null ? 0 : stub.compilingList;
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
        stub.compilingList = list;
        stub.compilingListMode = mode;
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
        stub.compilingList = 0;
        stub.compilingListMode = 0;
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
            stub.liveNames.remove(name);
            stub.deleteTexture(name);
        }
    }

    /** Records the transformer's glDeleteFramebuffers(count, pointer) route. */
    public static void celtium$glDeleteFramebuffers(int count, long names) {
        CeltiumGlStub stub = installed;
        if (stub == null || names == 0L) return;
        for (int i = 0; i < count; i++) {
            int name = memGetInt(names + (long) i * 4L);
            stub.liveNames.remove(name);
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
                || this.firstBindSites.containsKey(Integer.valueOf(texture))) {
            return;
        }
        this.firstBindSites.put(Integer.valueOf(texture), celtium$callSite() + " unit=" + this.textures.activeUnit());
    }

    /**
     * {@return the caller that first bound this texture name, or {@code "none"} when no bind was ever seen}. The
     * answer for the "no mirror" refusal's texture is the producer nobody could otherwise name.
     */
    public static String firstBindSite(int textureName) {
        CeltiumGlStub stub = installed;
        String site = stub == null ? null : stub.firstBindSites.get(Integer.valueOf(textureName));
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
        if (target == GL_ARRAY_BUFFER) {
            NativeGeometryBuffer.bindId(name);
        }
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        if (target == GL_PIXEL_PACK_BUFFER) {
            stub.pixelPackBuffer = name;
            return;
        }
        if (target == GL_PIXEL_UNPACK_BUFFER) {
            stub.pixelUnpackBuffer = name;
            return;
        }
        if (target >= 0 && target < stub.bufferBindings.length) {
            stub.bufferBindings[target] = name;
        }
    }

    /** Refusals caused by a bound pixel buffer, counted once so the diagnostic is bounded. */
    private int pixelBufferRefusals;
    /** Bounded count of the CELTIUM_PBO_UPLOAD lines: texture uploads read from a bound pixel unpack buffer. */
    private int pboUploadLogs;

    /** {@code GL_PIXEL_PACK_BUFFER}: with one bound, a readback's {@code pixels} argument is a buffer offset, not a pointer. */
    static final int GL_PIXEL_PACK_BUFFER = 0x88EB;

    /** {@code GL_PIXEL_UNPACK_BUFFER}: with one bound, a texture upload's {@code pixels} argument is a buffer offset. */
    static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;

    /** The bound pixel pack buffer, 0 when none is bound. */
    private int pixelPackBuffer;

    /** The bound pixel unpack buffer, 0 when none is bound. */
    private int pixelUnpackBuffer;

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

    /** Records the client vertex-array pointer, as the stub's own {@code glVertexPointer} case does. */
    public static void celtium$vertexPointer(int size, int type, int stride, long pointer) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.vertexPointerSize = size;
            stub.vertexPointerType = type;
            stub.vertexPointerStride = stride;
            stub.vertexPointerOffset = pointer;
        }
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
        return stub == null ? -1 : stub.textures.texture2d(0);
    }

    /** GL_SCISSOR_TEST: the enable bit that says whether the recorded rectangle clips. */
    private static final int GL_SCISSOR_TEST = 0x0C11;

    /**
     * GL_DEPTH_TEST: one of the capabilities a deferred draw can outlive. See the native state boundary.
     */
    private static final int GL_DEPTH_TEST = 0x0B71;

    /**
     * A hook the backend registers so a draw it defers - the coalesced font run - is submitted <em>before</em> a state
     * record it cannot see changes underneath it. The group's capture reads this stub live for blend, cull, colour
     * mask and scissor (BH-07/08/09/10), and reads the mirror for the rest; a caller that bypasses the mirror - a raw
     * {@code GL11} call - would otherwise let the pending run keep state it was appended under in the mirror while
     * the record the capture actually consults moved on. Fired before each tracked write, and only when the value
     * changes, so a no-op call does not split a run.
     * <p>
     * The measured defect this ordering produces when it is absent: the vanilla tooltip's font run survives Forge's
     * disableDepth/enableDepth bracket and is submitted depth-tested, where the shadow pass's equal-depth draw kills
     * the main pass's strokes - 585 of 797 shadow pixels lost their main ink in the HEI screen leg (instrument
     * {@code scripts/cpu-contract/font-shadow-main-check.py}).
     */
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
    private static final int GL_POLYGON_OFFSET_FILL = 0x8037;

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
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_BACK = 0x0405;
    private static final int GL_FRONT = 0x0404;
    private static final int GL_CCW = 0x0901;
    private static final int GL_CW = 0x0900;

    /** The game's clip rectangle as glScissor last set it, in GL's bottom-left coordinates. */

    /**
     * Initialize the two context rectangles from the actual drawable, once at window creation.
     * Later viewport changes must not alter the independent scissor box.
     */
    public static void initializeWindowRectangles(int width, int height) {
        CeltiumGlStub stub = installed;
        if (stub == null || width <= 0 || height <= 0) return;
        stub.viewportX = 0;
        stub.viewportY = 0;
        stub.viewportW = width;
        stub.viewportH = height;
        stub.raster.initializeScissor(width, height);
    }

    /** Records the viewport selected through Minecraft's API or LWJGL3's native entry point. */
    /** The last viewport the game set, packed as x|y|w|h in four 16-bit fields; 0 means "never set". */
    private static long viewportPacked;
    private static boolean viewportSeen;

    /** {@return the last viewport rectangle packed into one long, or {@code -1} when the game never set one}. */
    public static long celtium$viewportPacked() {
        return viewportSeen ? viewportPacked : -1L;
    }

    public static void celtium$viewport(int x, int y, int width, int height) {
        // Kept for the renderer's own viewport reader: the GL query goes through a direct IntBuffer allocation per
        // call, and the immediate path asks once per group. Four 16-bit fields are enough for any real viewport.
        if (width >= 0 && height >= 0 && width <= 0xFFFF && height <= 0xFFFF) {
            viewportPacked = ((long) (x & 0xFFFF) << 48) | ((long) (y & 0xFFFF) << 32)
                    | ((long) (width & 0xFFFF) << 16) | (height & 0xFFFF);
            viewportSeen = true;
        }
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.viewportX = x;
            stub.viewportY = y;
            stub.viewportW = width;
            stub.viewportH = height;
        }
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
    private static final int GL_BLEND = 0x0BE2;
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
        return stub != null && stub.capabilityObserved[GL_BLEND];
    }

    public static boolean cullEnableRecorded() {
        CeltiumGlStub stub = installed;
        return stub != null && stub.capabilityObserved[GL_CULL_FACE];
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

    /**
     * {@return whether GL_SCISSOR_TEST is enabled}. Read from the same {@code enables} map glEnable/glDisable write,
     * so there is one state rather than a mirror that can drift from it.
     */
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
     * {@return the buffers the game asked {@code glClear} to clear since this was last called}, consuming them.
     * <p>
     * With no GL framebuffer to clear, the request is carried to the owned frame image instead: an image whose depth
     * still holds undefined values rejects every depth-tested draw, which is a whole frame of nothing rather than a
     * wrong pixel.
     */
    /**
     * {@return the framebuffer the pending clear was issued against}, or -1 when no clear is pending. A non-zero
     * value means the clear names one of the game's own framebuffers rather than the delivered frame.
     */
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
        return stub.pendingClears.get() != 0 && stub.pendingClearIsMain;
    }

    public static int pendingClearDrawFbo() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return -1;
        }
        return stub.pendingClears.get() == 0 ? -1 : stub.pendingClearDrawFbo;
    }

    /** Records a clear through the same producer used by callback dispatch. */
    public static void celtium$clear(int mask) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.recordClear(mask);
        }
    }

    public static void celtium$clearColor(float red, float green, float blue, float alpha) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.recordClearColor(red, green, blue, alpha);
        }
    }

    public static void celtium$clearDepth(double depth) {
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.recordClearDepth(depth);
        }
    }

    private void recordClear(int mask) {
        // The routing owner, not raw name 0, distinguishes the game's main framebuffer from other targets.
        this.pendingClearDrawFbo = this.drawFbo;
        this.pendingClearIsMain = this.drawFramebufferIsMain;
        // How often the game clears what it calls its main framebuffer, and with what colour. The E2E scene's black
        // horizon needed exactly this: the model says the world frame clears that framebuffer with the fog colour every
        // frame, while a census of the routes said the clear never reached the delivered image.
        if (this.drawFramebufferIsMain) {
            this.mainClears++;
            // Per buffer: a later depth-only clear must not replace the colour the colour clear was issued with.
            if ((mask & 0x4000) != 0) {
                this.lastMainClearRed = this.clearRed;
                this.lastMainClearGreen = this.clearGreen;
                this.lastMainClearBlue = this.clearBlue;
                this.lastMainClearAlpha = this.clearAlpha;
                this.mainClearValuesKnown = true;
            }
            if ((mask & 0x0100) != 0) {
                this.lastMainClearDepth = this.clearDepth;
            }
        } else {
            this.foreignClears++;
            this.lastForeignClearRed = this.clearRed;
            this.lastForeignClearGreen = this.clearGreen;
            this.lastForeignClearBlue = this.clearBlue;
        }
        this.pendingClears.getAndUpdate(previous -> previous | mask);
        if (!this.drawFramebufferIsMain) {
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

    private void recordClearColor(float red, float green, float blue, float alpha) {
        this.clearRed = red;
        this.clearGreen = green;
        this.clearBlue = blue;
        this.clearAlpha = alpha;
    }

    private void recordClearDepth(double depth) {
        this.clearDepth = Math.max(0.0D, Math.min(1.0D, depth));
    }

    public static int consumePendingClears() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : stub.pendingClears.getAndSet(0);
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
        if (stub == null || !stub.mainClearValuesKnown) {
            clearColor(out);
            return Double.NaN;
        }
        out[0] = stub.lastMainClearRed;
        out[1] = stub.lastMainClearGreen;
        out[2] = stub.lastMainClearBlue;
        out[3] = stub.lastMainClearAlpha;
        return stub.lastMainClearDepth;
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
        out[0] = stub.clearRed;
        out[1] = stub.clearGreen;
        out[2] = stub.clearBlue;
        out[3] = stub.clearAlpha;
    }

    /** The depth the game last asked {@code glClear} to use. */
    public static double clearDepth() {
        CeltiumGlStub stub = installed;
        return stub == null ? 1.0 : stub.clearDepth;
    }

    /**
     * The RGBA8 pixels the game uploaded for a texture level, top row first, or {@code null} when nothing was captured
     * for it.
     * <p>
     * The upload is the source of truth for the Vulkan mirrors: GL-less mode has no driver to read back from, so the
     * bytes are the ones {@code glTexImage2D} and friends handed the stub. {@code outWidthHeight} receives the
     * captured extent; the returned buffer is a read-only duplicate positioned at 0, so a caller can neither move nor
     * modify the record. Weakly consistent and safe from any thread - a concurrent upload may land mid-call, but an
     * unknown texture name is just a {@code null} answer, and a compressed or unconverted upload is a {@code null}
     * answer too (the census says which).
     */
    public static java.nio.ByteBuffer textureUploadRgba8(int textureName, int level, int[] outWidthHeight) {
        CeltiumGlStub stub = installed;
        TexCapture capture = stub == null
                ? null
                : stub.texCaptures.get(texLevelKey(textureName, level));
        if (capture == null) {
            if (outWidthHeight != null && outWidthHeight.length >= 2) {
                // The extent goes with the pixels: a caller that reuses one array across levels must not read the
                // previous texture's size as this level's.
                outWidthHeight[0] = 0;
                outWidthHeight[1] = 0;
            }
            return null;
        }
        if (outWidthHeight != null && outWidthHeight.length >= 2) {
            outWidthHeight[0] = capture.width;
            outWidthHeight[1] = capture.height;
        }
        ByteBuffer view = capture.data.duplicate();
        view.clear();
        return view.asReadOnlyBuffer();
    }

    /**
     * {@return the texel offset the level's captured record covers inside its level}, (0,0) for a record that fills
     * the level from the origin. A capture that is a {@code glTexSubImage2D} patch into a level too big for the
     * capture budget is kept at patch size and carries the position the patch belongs at, so the mirror path writes
     * it into the extent-sized image as a region rather than posing the patch as the whole level - the case that
     * built the E2E atlas's mirror at 1125x1125. Returns false when no record exists.
     */
    public static boolean textureUploadOffset(int textureName, int level, int[] outXY) {
        CeltiumGlStub stub = installed;
        TexCapture capture = stub == null
                ? null
                : stub.texCaptures.get(texLevelKey(textureName, level));
        if (capture == null) {
            return false;
        }
        if (outXY != null && outXY.length >= 2) {
            outXY[0] = capture.x;
            outXY[1] = capture.y;
        }
        return true;
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

        private PendingPatch(TexCapture capture) {
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

    /**
     * {@return whether the level has backlog patches waiting}, without building the snapshot - the query the
     * supply paths ask before they may skip a full re-read.
     */
    public static boolean textureHasPendingPatches(int textureName, int level) {
        CeltiumGlStub stub = installed;
        java.util.List<TexCapture> pending = stub == null
                ? null
                : stub.texPatchBacklog.get(texLevelKey(textureName, level));
        return pending != null && !pending.isEmpty();
    }

    /**
     * {@return a snapshot of the level's backlog patches}, ordered by write serial. Empty when none are pending.
     * A snapshot keeps a concurrent {@code glTexSubImage2D} append from changing the list under the consumer;
     * a patch appended mid-iteration lands in the next call, which its newer serial makes the consumer take.
     */
    public static java.util.List<PendingPatch> texturePendingPatches(int textureName, int level) {
        CeltiumGlStub stub = installed;
        java.util.List<TexCapture> pending = stub == null
                ? null
                : stub.texPatchBacklog.get(texLevelKey(textureName, level));
        java.util.List<PendingPatch> out = new java.util.ArrayList<PendingPatch>(
                pending == null ? 0 : pending.size());
        if (pending != null) {
            for (TexCapture capture : pending) {
                out.add(new PendingPatch(capture));
            }
        }
        return out;
    }

    /**
     * Drops every backlog patch at or below {@code uptoSerial} for the level. A consumer calls this after writing
     * the patches into the mirror, which keeps the backlog at in-flight size instead of a second copy of the
     * texture. Patches appended after the call are untouched, so a write the consumer never saw is never pruned.
     */
    public static void texturePatchesConsumed(int textureName, int level, long uptoSerial) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return;
        }
        long key = texLevelKey(textureName, level);
        java.util.List<TexCapture> pending = stub.texPatchBacklog.get(key);
        if (pending == null) {
            return;
        }
        for (java.util.Iterator<TexCapture> it = pending.iterator(); it.hasNext();) {
            TexCapture capture = it.next();
            if (capture.serial <= uptoSerial) {
                stub.texPatchBytes -= capture.data.capacity();
                it.remove();
            }
        }
        if (pending.isEmpty()) {
            stub.texPatchBacklog.remove(key);
        }
    }

    /**
     * {@return whether the level holds pixels the game itself wrote}, false for the zero-filled surface a
     * {@code glTexImage2D} with a NULL data pointer left behind.
     * <p>
     * {@link #textureUploadRgba8} answers "is there a surface", and an allocation with no data gives one: the game
     * allocates every level of a texture that way and then fills it in patches, so the surface has to exist. What the
     * surface cannot say is whether any of it is the game's, and a mirror built from allocation zeros claims whole
     * content no upload ever put there - measured as two 1280x720 mirrors, and the light map's first read, reporting
     * {@code opaque=0} over {@code full=true}. A level with any real upload - a full image or a single patch - answers
     * true, because that surface is what GL holds: the patches where they were written, undefined (read as zeros)
     * elsewhere.
     *
     * @param textureName the GL texture name
     * @param level       the mip level
     */
    /**
     * {@return the write serial the level's captured record carries, or {@link Long#MIN_VALUE} when no record
     * exists at all}
     * <p>
     * {@link TexCapture#serial} documents the numbering. Two calls that answer the same number mean the level's
     * pixels are exactly what they were at the first call, which is all a mirror needs to decide an upload would
     * be a no-op. {@link Long#MIN_VALUE} never collides with a real serial - real numbers start at 1 - so a level
     * with no record reads as "unknown" rather than "unchanged".
     *
     * @param textureName the GL texture name
     * @param level       the mip level
     */
    /** {@return the serial the most recent level write took}, any texture: unchanged means no texture was written. */
    public static long textureWriteEpoch() {
        CeltiumGlStub stub = installed;
        return stub == null ? Long.MIN_VALUE : stub.texUploadSerial.get();
    }

    public static long textureUploadSerial(int textureName, int level) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return Long.MIN_VALUE;
        }
        long key = texLevelKey(textureName, level);
        long serial = Long.MIN_VALUE;
        TexCapture capture = stub.texCaptures.get(key);
        if (capture != null) {
            serial = capture.serial;
        }
        java.util.List<TexCapture> pending = stub.texPatchBacklog.get(key);
        if (pending != null && !pending.isEmpty()) {
            serial = Math.max(serial, pending.get(pending.size() - 1).serial);
        }
        return serial;
    }

    public static boolean textureLevelHasGamePixels(int textureName, int level) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return false;
        }
        TexCapture capture = stub.texCaptures.get(texLevelKey(textureName, level));
        return capture != null && capture.gamePixels;
    }

    /**
     * One line of capture diagnostics: the levels currently holding pixels, how many came from a full image, a
     * sub-image patch or an undefined level allocated with no data and zero-filled (the shape the game gives its
     * atlases before writing them sprite by sprite), the uploads that could not be converted (unsupported format/type
     * pair, a pixel-store state the conversion does not model, a compressed payload, a depth-carrying or oversized
     * level), and the RGBA8 bytes those levels hold.
     */
    public static String textureCaptureSummary() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "texture capture: levels=0 full=0 patches=0 undefined=0 unconverted=0 bytes=0";
        }
        long bytes = 0L;
        for (TexCapture capture : stub.texCaptures.values()) {
            bytes += capture.data.capacity();
        }
        int backlogPatches = 0;
        long topName = -1L, topCount = -1L;
        for (java.util.Map.Entry<Long, java.util.List<TexCapture>> e : stub.texPatchBacklog.entrySet()) {
            backlogPatches += e.getValue().size();
            if (e.getValue().size() > topCount) {
                topCount = e.getValue().size();
                topName = e.getKey().longValue();
            }
        }
        StringBuilder keys = new StringBuilder();
        java.util.List<java.util.Map.Entry<Long, Integer>> sorted =
                new java.util.ArrayList<java.util.Map.Entry<Long, Integer>>(stub.patchKeys.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().intValue(), a.getValue().intValue()));
        for (int i = 0; i < Math.min(4, sorted.size()); i++) {
            java.util.Map.Entry<Long, Integer> e = sorted.get(i);
            keys.append(" ").append(e.getKey().longValue() >>> 16).append('.')
                    .append(e.getKey().longValue() & 0xFFL).append('=').append(e.getValue());
            TexCapture stored = stub.texCaptures.get(e.getKey());
            keys.append(stored == null
                    ? "[none]"
                    : "[" + stored.width + "x" + stored.height + "@" + stored.x + "," + stored.y
                            + (stored.gamePixels ? "" : ",undef") + "]");
        }
        return "texture capture: levels=" + stub.texCaptures.size()
                + " full=" + stub.fullCaptures.get()
                + " patches=" + stub.subCaptures.get()
                + " identicalPatches=" + stub.identicalPatches.get()
                + " undefined=" + stub.undefinedExtents.get()
                + " unconverted=" + stub.unconvertedUploads.get()
                + " backlog=" + backlogPatches + "/" + stub.texPatchBytes + "B@" + (topName >>> 16)
                + "." + (topName & 0xFFL)
                + " patchKeys=" + keys
                + " branches=" + stub.patchBranches
                + " bytes=" + bytes;
    }

    private final org.soutalab.celtium.vulkan.NativeRasterState raster;
    private final org.soutalab.celtium.vulkan.NativeTextureBindings textures;

    private CeltiumGlStub() {
        this(org.soutalab.celtium.vulkan.NativeRasterState.current(),
                org.soutalab.celtium.vulkan.NativeTextureBindings.current());
    }

    private CeltiumGlStub(org.soutalab.celtium.vulkan.NativeRasterState raster) {
        this(raster, new org.soutalab.celtium.vulkan.NativeTextureBindings(raster));
    }

    private CeltiumGlStub(org.soutalab.celtium.vulkan.NativeRasterState raster,
            org.soutalab.celtium.vulkan.NativeTextureBindings textures) {
        this.raster = raster;
        this.textures = textures;
        // -1 is "no binding recorded": texture and buffer names are generated ids and never negative, while 0 is
        // the GL "unbound" name a caller can legitimately store.
        this.textureBindings.defaultReturnValue(-1);
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
        int id = IDS.containsKey(name) ? IDS.get(name).intValue() : -1;
        if (id < 0) {
            // BH-35: a name outside the table gets a generic no-op trampoline, and until now nothing recorded which
            // name it was - so a mod calling a GL entry point this stub does not model was swallowed with no trace at
            // all. The census names them (most frequent first) and the strict mode prints each one as it arrives, so
            // "unsupported" is a recorded boundary rather than a silent success.
            celtium$noteUnknownSymbol(name);
        }
        if (Boolean.getBoolean("celtium.glstub.trace")
                && ("glGetError".equals(name) || "glGetIntegerv".equals(name) || "glGetString".equals(name))) {
            System.out.println("[GLSTUB] addressFor " + name + " id=" + id);
        }
        byte[] args = id >= 0 ? TABLE.get(id).args : new byte[0];
        if (celtium$ffmCompatible(args)) {
            Long stub = this.ffmAddresses.computeIfAbsent(name, n -> {
                long address = celtium$ffmStub(id >= 0 ? id : -1, args);
                return address != 0 ? Long.valueOf(address) : null;
            });
            if (stub != null) {
                return stub.longValue();
            }
        }
        if (args.length > 0 && args[0] == A_DOUBLE) {
            return this.trampolines.computeIfAbsent(name, n -> {
                CeltiumGlDoubleTrampoline trampoline = new CeltiumGlDoubleTrampoline(id, this);
                this.ownedTrampolines.add(trampoline);
                return trampoline;
            }).address();
        }
        if (args.length > 0 && args[0] == A_FLOAT) {
            return this.trampolines.computeIfAbsent(name, n -> {
                CeltiumGlFloatTrampoline trampoline = new CeltiumGlFloatTrampoline(id, this);
                this.ownedTrampolines.add(trampoline);
                return trampoline;
            }).address();
        }
        return this.trampolines.computeIfAbsent(name, n -> {
            CeltiumGlTrampoline trampoline = new CeltiumGlTrampoline(id, this, args);
            this.ownedTrampolines.add(trampoline);
            return trampoline;
        }).address();
    }

    // ---- FFM upcalls -----------------------------------------------------------------

    /**
     * {@code -Dceltium.glstub.ffm=false} forces every trampoline onto the libffi path. The default is FFM:
     * LWJGL's runtime binder turns the same SAM + CIF into a {@code java.lang.foreign} upcall, which the steady
     * JFR showed paying a fraction of the legacy path's libffi closure plus per-argument pointer dereferences.
     */
    private static final boolean FFM_UPCALLS = !"false".equals(System.getProperty("celtium.glstub.ffm", "true"));

    /**
     * {@return whether the signature can ride the FFM upcall}: the FFM path shares the uniform long-argument SAM, so
     * it can only carry calls whose every argument the ABI puts in a general-purpose register. Float and double
     * arguments live in the SSE registers - a long-typed parameter reads the wrong slot (the glClearColor colour that
     * arrived as -1.6e29 before the CIF declared the floats) - and those stay on the libffi trampolines whose own
     * CIFs marshal them. NOOP rows are exempt anyway: they discard every argument, so their declared
     * all-pointer signature already reads general-purpose registers correctly.
     */
    private static boolean celtium$ffmCompatible(byte[] args) {
        if (!FFM_UPCALLS) {
            return false;
        }
        for (byte arg : args) {
            if (arg == A_FLOAT || arg == A_DOUBLE) {
                return false;
            }
        }
        return true;
    }

    /** Reflective handles into {@code org.lwjgl.system.ffm}: present on JDK 25+, absent on older runtimes. */
    private static final class FfmRuntime {
        private java.lang.reflect.Method ffmUpcall;
        private java.lang.reflect.Method binderAllocate;
        private java.lang.reflect.Method segmentAddress;
        private Object arena;
        private Throwable failure;
    }

    private static volatile FfmRuntime ffmRuntime;

    private static FfmRuntime celtium$ffmRuntime() {
        FfmRuntime runtime = ffmRuntime;
        if (runtime == null) {
            synchronized (CeltiumGlStub.class) {
                runtime = ffmRuntime;
                if (runtime == null) {
                    runtime = new FfmRuntime();
                    try {
                        Class<?> ffmClass = Class.forName("org.lwjgl.system.ffm.FFM");
                        Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");
                        Class<?> segmentClass = Class.forName("java.lang.foreign.MemorySegment");
                        runtime.arena = arenaClass.getMethod("ofAuto").invoke(null);
                        runtime.segmentAddress = segmentClass.getMethod("address");
                        runtime.ffmUpcall = ffmClass.getMethod("ffmUpcall",
                                Class.class, org.lwjgl.system.libffi.FFICIF.class);
                        runtime.binderAllocate = Class.forName("org.lwjgl.system.ffm.UpcallBinder")
                                .getMethod("allocate", arenaClass, Object.class);
                        // The binder generates its upcall class under a per-package configuration: nothing is
                        // registered for our package until this runs, and the unconfigured call fails with
                        // "No FFMConfig registered". One default config is enough - the stub needs no symbol
                        // lookup, no tracing and no nullable mapping.
                        Object builder = ffmClass.getMethod("ffmConfigBuilder",
                                java.lang.invoke.MethodHandles.Lookup.class)
                                .invoke(null, java.lang.invoke.MethodHandles.lookup());
                        Object config = builder.getClass().getMethod("build").invoke(builder);
                        ffmClass.getMethod("ffmConfig", Package.class,
                                Class.forName("org.lwjgl.system.ffm.FFMConfig"))
                                .invoke(null, CeltiumGlStub.class.getPackage(), config);
                    } catch (Throwable error) {
                        runtime.failure = error;
                        System.out.println("[Celtium] GLSTUB ffm runtime unavailable: " + error);
                    }
                    ffmRuntime = runtime;
                }
            }
        }
        return runtime;
    }

    /**
     * Creates one FFM upcall for {@code functionId} with the signature's own CIF and returns its callable address,
     * or 0 when the runtime or the binder refused - the caller then takes the libffi trampoline for the same name.
     * The SAM instance, the CIF and the returned segment all stay referenced for the stub's lifetime.
     */
    private long celtium$ffmStub(int functionId, byte[] args) {
        FfmRuntime runtime = celtium$ffmRuntime();
        if (runtime == null || runtime.failure != null) {
            return 0L;
        }
        try {
            int id = functionId;
            // dispatch() fills the shared argument scratch - allocating a fresh array per call here was exactly the
            // ~4 MB/frame of garbage the scratch exists to avoid.
            GlTrampolineI callback = (a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11) ->
                    dispatch(id, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11);
            Object binder = runtime.ffmUpcall.invoke(null, GlTrampolineI.class, cifFor(args));
            Object segment = runtime.binderAllocate.invoke(binder, runtime.arena, callback);
            long address = ((Long) runtime.segmentAddress.invoke(segment)).longValue();
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=ffm (the java.lang.foreign upcall)");
            }
            this.ffmOwned.add(segment);
            this.ffmOwned.add(callback);
            return address;
        } catch (Throwable error) {
            if (runtime.failure == null) {
                runtime.failure = error;
            }
            this.ffmFailures++;
            if (this.ffmFailures <= 3) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                System.out.println("[Celtium] GLSTUB ffmStub failed for functionId=" + functionId + ": "
                        + cause.getClass().getName() + " " + cause.getMessage());
            }
            return 0L;
        }
    }

    /** Function-name -> callable address for the FFM stubs; misses fall through to the libffi trampolines. */
    private final Map<String, Long> ffmAddresses = new HashMap<>();
    /** Objects whose lifetime pins the FFM stubs: binder segments and their SAM instances. */
    private final List<Object> ffmOwned = new ArrayList<>();
    private int ffmFailures;

    // ---- trampoline plumbing ---------------------------------------------------------

    /**
     * Argument slots in the plain (integer/pointer) upcall shape.
     * <p>
     * This is set by the widest row in the table, {@code glTexImage3D} at ten arguments - and it has to be that wide
     * for the texture path to work at all: {@code glTexImage2D} takes nine arguments, so its {@code pixels} pointer is
     * slot 8 and an upcall narrower than that never receives it. Slots past a call's declared arguments carry whatever
     * the caller left in the registers and stack; no handler reads past its own row's declaration, so they are inert.
     */
    private static final int SLOTS = 12;

    /**
     * One uniform call interface covers every function in the table: the FFM upcall invokes the SAM with the C
     * arguments marshalled per the CIF, so a CIF of twelve argument slots means {@code invoke} receives up to twelve
     * argument slots. Callers of the real functions supply fewer - the extra slots carry garbage that the dispatcher
     * ignores; callers expecting narrower results (int or nothing) read only the low bits or discard the value
     * entirely. The widest function in the table is {@code glTexImage3D} at ten arguments.
     */
    private static final Callback.Descriptor UNIFORM_DESCRIPTOR = new Callback.Descriptor(
            GlTrampolineI.class,
            java.lang.invoke.MethodHandles.lookup(),
            apiCreateCIF(ffi_type_pointer, new FFIType[] {
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer }));

    /**
     * One descriptor per declared signature, so the trampoline reads the arguments where the caller actually put
     * them.
     * <p>
     * The uniform pointer interface above is only correct for functions that take integers and pointers: an x86-64
     * caller passes floating-point arguments in the SSE registers, so a closure that declares pointers reads the
     * general-purpose registers and sees whatever was left in them. That is not theoretical - it is what turned the
     * game's {@code glClearColor} into an arbitrary colour. The table already declares every argument's type, so the
     * CIF is built from it and float arguments arrive as floats.
     */
    private static final java.util.Map<String, Callback.Descriptor> SIGNATURE_DESCRIPTORS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static Callback.Descriptor descriptorFor(byte[] args) {
        StringBuilder key = new StringBuilder(args.length + 1);
        for (byte arg : args) {
            key.append((char) ('a' + arg));
        }
        return SIGNATURE_DESCRIPTORS.computeIfAbsent(key.toString(), ignored ->
                new Callback.Descriptor(trampolineType(args), java.lang.invoke.MethodHandles.lookup(), cifFor(args)));
    }

    /**
     * {@return the trampoline interface a signature's arguments are read by}.
     * <p>
     * LWJGL 3.4.3's {@code Callback.Descriptor} carries the callback type as well as the CIF, because the trampoline is
     * built from the descriptor rather than from the interface alone. Which interface it is follows from where the
     * arguments actually land: an x86-64 caller passes floats and doubles in the SSE registers, so those signatures
     * need the upcalls whose abstract method declares them - the same reason {@link #ffiTypeFor} distinguishes them.
     */
    private static Class<? extends org.lwjgl.system.CallbackI> trampolineType(byte[] args) {
        boolean floats = false;
        boolean doubles = false;
        for (byte arg : args) {
            if (arg == A_FLOAT) {
                floats = true;
            } else if (arg == A_DOUBLE) {
                doubles = true;
            }
        }
        if (doubles) {
            return GlTrampolineDoubleI.class;
        }
        return floats ? GlTrampolineFloatI.class : GlTrampolineI.class;
    }

    /** The signature's call interface itself, so the FFM binder can see the CIF the Callback descriptor wraps. */
    private static FFICIF cifFor(byte[] args) {
        java.util.List<FFIType> types = new java.util.ArrayList<FFIType>(SLOTS);
        for (byte arg : args) {
            if (types.size() == SLOTS) {
                break;
            }
            types.add(ffiTypeFor(arg));
        }
        // Padded to the uniform arity: the dispatcher ignores the slots past the declared arguments, and the
        // native side reads only what the caller passed.
        while (types.size() < SLOTS) {
            types.add(ffi_type_pointer);
        }
        return apiCreateCIF(ffi_type_pointer, types.toArray(new FFIType[0]));
    }

    private static FFIType ffiTypeFor(byte arg) {
        switch (arg) {
            case A_FLOAT:
                return ffi_type_float;
            case A_DOUBLE:
                return ffi_type_double;
            case A_LONG:
                return ffi_type_sint64;
            case A_PTR:
                return ffi_type_pointer;
            default:
                return ffi_type_sint32;
        }
    }

    /**
     * The upcall interface the FFM backend binds: one abstract method receiving the call's argument values. The
     * legacy {@code callback} entry is not used by the FFM path and stays a stub.
     */
    @FunctionalInterface
    public interface GlTrampolineI extends org.lwjgl.system.CallbackI {
        long invoke(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7,
                long a8, long a9, long a10, long a11);

        /**
         * Required by {@link CallbackI}; the trampoline class overrides it with the legacy-backend entry. A default
         * must exist so this stays a functional interface.
         */
        @Override
        default void callback(long result, long args) {
        }

        /** Supplied by the trampoline instance. */
        @Override
        default Callback.Descriptor getDescriptor() {
            return UNIFORM_DESCRIPTOR;
        }
    }

    /**
     * The upcall shape for functions that take floating-point arguments.
     * <p>
     * The long-argument shape cannot deliver them: an x86-64 caller passes floats and doubles in the SSE registers,
     * and an upcall whose parameters are all {@code long} reads the general-purpose ones instead, so the values
     * arrive as whatever was left in them (measured: {@code glClearColor(0.25, 0.5, 0.75, 1)} arriving as
     * {@code -1.6e29}). The CIF declares the floats, so the runtime marshals them where they belong. The shape carries
     * eight slots, which is every float-argument row's width; the dispatcher's wider slots stay zero for these.
     */
    private static final Callback.Descriptor FLOAT4_DESCRIPTOR = new Callback.Descriptor(
            GlTrampolineFloatI.class,
            java.lang.invoke.MethodHandles.lookup(),
            apiCreateCIF(ffi_type_pointer, new FFIType[] {
                    ffi_type_float, ffi_type_float, ffi_type_float, ffi_type_float,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer }));

    private static final Callback.Descriptor DOUBLE1_DESCRIPTOR = new Callback.Descriptor(
            GlTrampolineDoubleI.class,
            java.lang.invoke.MethodHandles.lookup(),
            apiCreateCIF(ffi_type_pointer, new FFIType[] {
                    ffi_type_double, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer }));

    /** The four-float upcall: {@code glClearColor} and the single-float setters share it. */
    @FunctionalInterface
    public interface GlTrampolineFloatI extends org.lwjgl.system.CallbackI {
        long invoke(float a0, float a1, float a2, float a3, long a4, long a5, long a6, long a7);

        @Override
        default void callback(long result, long args) {
        }

        @Override
        default Callback.Descriptor getDescriptor() {
            return FLOAT4_DESCRIPTOR;
        }
    }

    /** The double upcall: {@code glClearDepth} is the one in the table. */
    @FunctionalInterface
    public interface GlTrampolineDoubleI extends org.lwjgl.system.CallbackI {
        long invoke(double a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7);

        @Override
        default void callback(long result, long args) {
        }

        @Override
        default Callback.Descriptor getDescriptor() {
            return DOUBLE1_DESCRIPTOR;
        }
    }

    private static final class CeltiumGlFloatTrampoline extends Callback implements GlTrampolineFloatI {
        private final int functionId;
        private final CeltiumGlStub stub;

        CeltiumGlFloatTrampoline(int functionId, CeltiumGlStub stub) {
            super(FLOAT4_DESCRIPTOR);
            this.functionId = functionId;
            this.stub = stub;
        }

        @Override
        public long invoke(float a0, float a1, float a2, float a3, long a4, long a5, long a6, long a7) {
            // Every float-argument row declares at most four arguments, so this narrow upcall stays as measured and
            // the wide slots the dispatcher carries are zero rather than junk.
            return this.stub.dispatch(this.functionId,
                    Float.floatToRawIntBits(a0) & 0xFFFFFFFFL, Float.floatToRawIntBits(a1) & 0xFFFFFFFFL,
                    Float.floatToRawIntBits(a2) & 0xFFFFFFFFL, Float.floatToRawIntBits(a3) & 0xFFFFFFFFL,
                    a4, a5, a6, a7, 0L, 0L, 0L, 0L);
        }

        @Override
        public void callback(long result, long args) {
            this.stub.legacyDispatch(this.functionId, result, args);
        }
    }

    private static final class CeltiumGlDoubleTrampoline extends Callback implements GlTrampolineDoubleI {
        private final int functionId;
        private final CeltiumGlStub stub;

        CeltiumGlDoubleTrampoline(int functionId, CeltiumGlStub stub) {
            super(DOUBLE1_DESCRIPTOR);
            this.functionId = functionId;
            this.stub = stub;
        }

        @Override
        public long invoke(double a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7) {
            // One double argument is the widest row this shape serves; the wide slots stay zero (see the float entry).
            return this.stub.dispatch(this.functionId, Double.doubleToRawLongBits(a0), a1, a2, a3, a4, a5, a6, a7,
                    0L, 0L, 0L, 0L);
        }

        @Override
        public void callback(long result, long args) {
            this.stub.legacyDispatch(this.functionId, result, args);
        }
    }

    /**
     * One native trampoline per function name. Two call entries exist because which backend LWJGL uses is a
     * runtime property, not a compile-time one: {@code invoke} serves the FFM backend, {@code callback} the
     * legacy libffi one.
     */
    /**
     * The upcall object, implementing every trampoline interface.
     * <p>
     * LWJGL 3.4.3's {@code Callback} casts the instance to the type its descriptor names, so one object has to satisfy
     * whichever interface the signature's descriptor was built with - the uniform long upcall for pointers and
     * integers, and the float and double upcalls for the signatures whose arguments a caller passes in the SSE
     * registers. The float and double entries convert to raw bits and hand them to the same dispatch, which is what
     * the uniform upcall received before the descriptor carried a type at all.
     */
    private static final class CeltiumGlTrampoline extends Callback
            implements GlTrampolineI, GlTrampolineFloatI, GlTrampolineDoubleI {
        private final int functionId;
        private final CeltiumGlStub stub;
        private final Callback.Descriptor descriptor;

        CeltiumGlTrampoline(int functionId, CeltiumGlStub stub, byte[] args) {
            super(descriptorFor(args));
            this.functionId = functionId;
            this.stub = stub;
            this.descriptor = descriptorFor(args);
        }

        @Override
        public Callback.Descriptor getDescriptor() {
            return this.descriptor;
        }

        /** The FFM backend's entry: the C call's arguments arrive as this SAM's parameters, in order. */
        @Override
        public long invoke(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7,
                long a8, long a9, long a10, long a11) {
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=ffm (the uniform long-argument upcall)");
            }
            return this.stub.dispatch(this.functionId, a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11);
        }

        /** The float-argument entry: four floats, converted to raw bits for the uniform dispatch. */
        @Override
        public long invoke(float a0, float a1, float a2, float a3, long a4, long a5, long a6, long a7) {
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=ffm (the float-argument upcall)");
            }
            return this.stub.dispatch(this.functionId,
                    Float.floatToRawIntBits(a0), Float.floatToRawIntBits(a1),
                    Float.floatToRawIntBits(a2), Float.floatToRawIntBits(a3),
                    a4, a5, a6, a7, 0L, 0L, 0L, 0L);
        }

        /** The double-argument entry: one double, converted to raw bits for the uniform dispatch. */
        @Override
        public long invoke(double a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7) {
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=ffm (the double-argument upcall)");
            }
            return this.stub.dispatch(this.functionId, Double.doubleToRawLongBits(a0),
                    a1, a2, a3, a4, a5, a6, a7, 0L, 0L, 0L, 0L);
        }

        /**
         * The legacy (libffi) backend's entry: {@code args} is an array of pointers, one per C argument, each
         * pointing at that argument's storage; the result goes to the closure-owned slot {@code result}.
         */
        @Override
        public void callback(long result, long args) {
            if (ENTRY_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Celtium] GLSTUB entry=legacy (the libffi pointer-array callback)");
            }
            this.stub.legacyDispatch(this.functionId, result, args);
        }
    }

    // ---- dispatch ---------------------------------------------------------------------

    /** One line saying which upcall entry the runtime actually uses; the two need different argument handling. */
    private static final java.util.concurrent.atomic.AtomicBoolean ENTRY_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();

    private static final int TRACE_LIMIT = Integer.getInteger("celtium.glstub.traceLimit", 40).intValue();
    /**
     * The trace switch, read once: {@link Boolean#getBoolean} is a map lookup and this method's callers run it on
     * every GL call the game makes - measured at ~2.6% of the warm main-thread samples as part of the dispatch cost.
     */
    private static final boolean TRACE = Boolean.getBoolean("celtium.glstub.trace");

    private final java.util.concurrent.atomic.AtomicInteger dispatchCount = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * The legacy (libffi) entry. {@code args} is an array of pointers, one per C argument, each pointing at that
     * argument's storage: the value is read out of the storage by declared type, then the call goes through the
     * same {@link #dispatch} as the FFM entry. The CIF return is always a pointer, so the result slot is always
     * written - narrower C callers read its low bits.
     */
    void legacyDispatch(int functionId, long result, long args) {
        if (functionId < 0) {
            APIUtil.apiClosureRetP(result, 0L);
            return;
        }
        Fn fn = TABLE.get(functionId);
        long[] c = dispatchArgsScratch();
        for (int i = 0; i < fn.args.length && i < SLOTS; i++) {
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
        APIUtil.apiClosureRetP(result, dispatchArgs(fn, c));
    }

    /**
     * Handles a call. {@code c0..c11} are the call's argument values in order - ints widened to longs, pointers as
     * raw addresses. Only the first {@code fn.args.length} slots are meaningful: the rest carry whatever the upcall
     * read past the arguments the C caller actually passed (or an explicit zero from the float/double entries). The
     * return value goes into the caller's result register; narrower results use the low bits.
     */
    private long dispatch(int functionId, long c0, long c1, long c2, long c3, long c4, long c5, long c6, long c7,
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
     * {@code new long[SLOTS]}, measured at tens of thousands of GL calls per frame - ~4 MB of garbage per frame
     * feeding every GC pause the p99 bands blame on the game. The first thread to call owns the shared array (a
     * field read and an identity compare, cheaper than {@code ThreadLocal.get} on a path this hot); a call on any
     * other thread falls back to a fresh array so a class-loading thread can never race the render thread's
     * arguments. Re-use is safe because dispatch handlers never call back through a C trampoline - a nested
     * {@link #legacyDispatch} would have to originate inside a handler, and handlers are pure Java - so one array
     * per owning thread cannot be corrupted by re-entry.
     */
    private final long[] dispatchScratch = new long[SLOTS];
    private Thread dispatchScratchOwner;

    /**
     * {@code -Dceltium.debug.fnCensus=true}: count arrivals per table entry and print the top producers at exit.
     * The per-call cost is one field increment on the Fn the dispatch already resolved - the question "which
     * functions make up the tens of thousands of trampoline calls per frame" has never had a counted answer.
     */
    private static final boolean FN_CENSUS = Boolean.getBoolean("celtium.debug.fnCensus");
    /** Per-pname arrival counts for {@code glGetIntegerv}, under {@link #FN_CENSUS}; see its call site. */
    private final java.util.Map<Integer, Long> getIntegervPnames = new java.util.HashMap<>();

    private long[] dispatchArgsScratch() {
        Thread calling = Thread.currentThread();
        if (calling == this.dispatchScratchOwner) {
            return this.dispatchScratch;
        }
        if (this.dispatchScratchOwner == null) {
            this.dispatchScratchOwner = calling;
            return this.dispatchScratch;
        }
        return new long[SLOTS];
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
     * The dispatch with the table entry already looked up - the legacy entry needs the {@link Fn} for its arg-count
     * loop, so resolving it twice per call paid a second map probe on every GL call.
     */
    private long dispatchArgs(Fn fn, long[] c) {
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
                if (this.compilingListMode != GL_COMPILE_AND_EXECUTE) return 0L;
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
                if (this.compilingListMode != GL_COMPILE_AND_EXECUTE) {
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
                    this.getIntegervPnames.merge((int) a0, 1L, Long::sum);
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
                return this.liveNames.contains((int) a0) || this.liveSync.contains(a0)
                        || "glIsBuffer".equals(fn.name) && NativeGeometryBuffer.find((int) a0) != null ? 1L : 0L;
            case SEM_DELETE:
                delete(fn, c);
                return 0L;
            case SEM_BIND_BUFFER:
                if ((int) a0 == GL_ARRAY_BUFFER) {
                    NativeGeometryBuffer.bindId((int) a1);
                }
                if ((int) a0 >= 0 && (int) a0 < this.bufferBindings.length) {
                    this.bufferBindings[(int) a0] = (int) a1;
                }
                return 0L;
            case SEM_BIND_TEXTURE:
                if ((int) a0 == GL_TEXTURE_2D) this.textures.bindTexture2d((int) a1);
                else this.textureBindings.put(texBindKey((int) a0), (int) a1);
                noteBindSite((int) a1);
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
                this.viewportX = (int) c[0];
                this.viewportY = (int) c[1];
                this.viewportW = (int) c[2];
                this.viewportH = (int) c[3];
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
                this.matrixMode = (int) c[0];
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
                this.lineWidth = Float.intBitsToFloat((int) c[0]);
                return 0L;
            case SEM_USE_PROGRAM:
                this.currentProgram = (int) c[0];
                return 0L;
            case SEM_NEW_LIST:
                // A name of zero is not a list (the spec makes glNewList(0) GL_INVALID_VALUE), and recording under it
                // would file calls where no glCallList can reach them - which is the state this stub started in.
                this.openListGeometry = null;
                this.compilingList = (int) c[0];
                this.compilingListMode = (int) c[1]; // GL_LIST_MODE answers this, and the model capture asks for it
                this.openList = this.compilingList == 0 ? null : new ArrayList<Object[]>();
                return 0L;
            case SEM_END_LIST:
                // The spec replaces a name's contents only at glEndList, so the previous list stays callable until
                // this point; putting the new one in here is that rule, not an optimisation.
                List<Object[]> compiled = this.openList;
                if (compiled != null) {
                    // The spec replaces a name's contents at glEndList, so the payloads of the list this one displaces
                    // are unreachable from here on and are released now rather than held for the life of the process.
                    releaseListPayloads(Integer.valueOf(this.compilingList));
                    this.displayLists.put(Integer.valueOf(this.compilingList), compiled);
                    if (this.openListGeometry != null) this.displayListGeometry.put(this.compilingList, this.openListGeometry);
                    if (this.openListPayloads != null) {
                        this.displayListPayloads.put(Integer.valueOf(this.compilingList), this.openListPayloads);
                    }
                    this.displayListsCompiled++;
                } else if (this.openListPayloads != null) {
                    for (int i = 0; i < this.openListPayloads.size(); i++) {
                        org.lwjgl.system.MemoryUtil.memFree(this.openListPayloads.get(i));
                    }
                }
                if (compiled == null && this.openListGeometry != null)
                    for (NativeGeometryBuffer buffer : this.openListGeometry) buffer.close();
                this.openListGeometry = null;
                this.openListPayloads = null;
                this.openList = null;
                this.compilingList = 0;
                return 0L;
            case SEM_CALL_LIST:
                replayDisplayList((int) c[0]);
                return 0L;
            case SEM_BIND_VERTEX_ARRAY:
                this.boundVertexArray = (int) c[0];
                return 0L;
            case SEM_VERTEX_POINTER:
                // GL keeps one client vertex-array pointer; the last glVertexPointer wins, exactly as it does in a
                // driver. The pointer is a byte offset into the bound array buffer (GL_ARRAY_BUFFER) - the value the
                // game passes when a buffer is bound, which is the only case drawArrays() decodes.
                this.vertexPointerSize = (int) c[0];
                this.vertexPointerType = (int) c[1];
                this.vertexPointerStride = (int) c[2];
                this.vertexPointerOffset = c[3];
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
                celtium$noteImmediateBegin((int) c[0]);
                return 0L;
            case SEM_END:
                BEGIN_END_CALLS.incrementAndGet();
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
    private static final java.util.Set<String> MATRIX_MULTIPLY_SHAPES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        if (Boolean.getBoolean("celtium.vulkan.strictSymbols")
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
    private static String celtium$callSite() {
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

    /** The name-keyed no-op path remains for callers that genuinely have a name: it folds into the id arrays. */
    private static void celtium$noteNoop(String name) {
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

    // ---- the glDrawArrays census ---------------------------------------------------------------------------------
    //
    // glDrawArrays left the no-op census because it is now drawn, and that is a claim the run has to be able to
    // check: "no longer in the no-op list" is also what a draw that was refused everywhere would look like. The
    // counters below separate the three outcomes - drawn, empty, refused with a named reason - and the accepted
    // count is the one that says the geometry reached the frame. Read them together; none of them alone is evidence.

    /** Arrivals at {@code glDrawArrays} since the process started. */
    private static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_CALLS =
            new java.util.concurrent.atomic.AtomicLong();
    /** Draws assembled from the mirrored array buffer and handed to the immediate path (accepted by it). */
    private static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_DRAWN =
            new java.util.concurrent.atomic.AtomicLong();
    /** Draws with no vertices at all: legitimate, and nothing is dropped because there was nothing to draw. */
    private static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_EMPTY =
            new java.util.concurrent.atomic.AtomicLong();
    /** Draws this class would not decode, or the backend would not take. Each one names its reason below. */
    private static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_REFUSED =
            new java.util.concurrent.atomic.AtomicLong();
    /** Vertices in the draws that were handed over, so "drawn" is a quantity and not only a call count. */
    private static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_VERTICES =
            new java.util.concurrent.atomic.AtomicLong();
    /** Refusals by reason, bounded by the number of distinct reasons this class can produce. */
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> DRAWARRAY_REFUSALS =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();
    /** The first accepted draw, in one line: the evidence that the layout decoded as position-only really did. */
    private static volatile String drawArrayFirst = "none";

    /** glBegin/glEnd arrivals, and the distinct (mode, site) pairs already named, bounded to eight. */
    private static final java.util.concurrent.atomic.AtomicLong BEGIN_END_CALLS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.Set<String> IMMEDIATE_SITES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.atomic.AtomicLong IMMEDIATE_VERTICES =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Names the site that opens a legacy immediate-mode run, once per (mode, site).
     * <p>
     * Bounded to eight lines and deduplicated, so a menu frame that opens thousands of runs adds eight lines rather
     * than thousands. The point is attribution, not volume: the loading screen's three {@code glBegin(GL_QUADS)}
     * sites in Forge's {@code SplashProgress} have to be visible as callers before the recording and submit steps
     * can be judged - the no-op census's {@code glBegin=38115} says how many, not who.
     */
    private static void celtium$noteImmediateBegin(int mode) {
        BEGIN_END_CALLS.incrementAndGet();
        if (IMMEDIATE_SITES.size() >= 8) {
            return;
        }
        String site = mode + "@" + celtium$callSite(6);
        if (IMMEDIATE_SITES.add(site)) {
            System.out.println("[Celtium] CELTIUM_IMMEDIATE_BEGIN mode=" + mode + "(" + immediateModeName(mode)
                    + ") calls=" + BEGIN_END_CALLS.get() + " site=[" + celtium$callSite(6) + "]");
        }
    }

    /** {@return the GL primitive mode's name}, so the census line is readable without the enum. */
    private static String immediateModeName(int mode) {
        switch (mode) {
            case 0x0000: return "GL_POINTS";
            case 0x0001: return "GL_LINES";
            case 0x0003: return "GL_LINE_STRIP";
            case 0x0004: return "GL_TRIANGLES";
            case 0x0005: return "GL_TRIANGLE_STRIP";
            case 0x0006: return "GL_TRIANGLE_FAN";
            case 0x0007: return "GL_QUADS";
            case 0x0008: return "GL_QUAD_STRIP";
            case 0x0009: return "GL_POLYGON";
            default: return "mode" + mode;
        }
    }

    private static void celtium$noteDrawArrayRefusal(String reason) {
        DRAWARRAY_REFUSED.incrementAndGet();
        java.util.concurrent.atomic.AtomicLong counter = DRAWARRAY_REFUSALS.get(reason);
        if (counter == null) {
            counter = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicLong existing = DRAWARRAY_REFUSALS.putIfAbsent(reason, counter);
            if (existing != null) {
                counter = existing;
            }
        }
        counter.incrementAndGet();
    }

    /**
     * Prints the census line at powers of two of the arrival count, in addition to the line at exit.
     * <p>
     * The exit line is the complete one and the one to read. This exists because a run can end without reaching it -
     * the process is torn down, or the fixture exits - and "the counter has no value" was the shape of a previous
     * mistake here. Powers of two bound the output to about twenty lines for any run length, and put the first line
     * in the log after the very first draw rather than after the last.
     */
    private static void celtium$reportDrawArrays() {
        long calls = DRAWARRAY_CALLS.get();
        if (!org.soutalab.celtium.vulkan.VulkanDiagnostics.enabled()) {
            // The powers-of-two lines are the repeating half of this instrument; the exit line below is not gated, so
            // a support log still carries the census once. See VulkanDiagnostics for why the gate exists.
            return;
        }
        if (calls > 0L && (calls & (calls - 1L)) == 0L) {
            System.out.println("[Celtium] CELTIUM_DRAWARRAYS " + drawArraySummary());
        }
    }

    /**
     * {@return the {@code glDrawArrays} census as one line: arrivals, drawn, empty, refused and why}.
     * <p>
     * The refusals are listed with their reasons rather than counted, because the reasons are the point: a draw this
     * class does not decode is a named gap, and a bare number would leave the next reader to guess which layout or
     * which mode it was. Sorted, so two runs of the same scene compare line by line.
     */
    public static String drawArraySummary() {
        StringBuilder reasons = new StringBuilder();
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry
                : new java.util.TreeMap<String, java.util.concurrent.atomic.AtomicLong>(DRAWARRAY_REFUSALS).entrySet()) {
            if (reasons.length() > 0) {
                reasons.append("; ");
            }
            reasons.append(entry.getKey()).append('=').append(entry.getValue().get());
        }
        return "glDrawArraysCalls=" + DRAWARRAY_CALLS.get()
                + " drawn=" + DRAWARRAY_DRAWN.get()
                + " vertices=" + DRAWARRAY_VERTICES.get()
                + " empty=" + DRAWARRAY_EMPTY.get()
                + " refused=" + DRAWARRAY_REFUSED.get()
                + " pureBlueGroups=" + DRAWARRAY_PURE_BLUE.get()
                + " clientMemoryDecoded=" + clientPointerAccepted
                + " ownedBufferDecoded=" + ownedClientBufferDecoded
                + " pointerOnlyDecoded=" + pointerOnlyDecoded
                + " ownedBufferMismatch=" + ownedClientBufferMismatch
                + " readableRanges=" + readableMappingRanges
                + " mappingFailure=" + readableMappingFailure
                + " first=[" + drawArrayFirst + "]"
                + " refusedByReason=[" + (reasons.length() == 0 ? "none" : reasons.toString()) + "]";
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
    /** The stack the stub last saw selected; GL starts in MODELVIEW. */
    private volatile int matrixMode = GL_MODELVIEW;

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
        return stub != null && stub.matrixMode == mode;
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
                    + " mode=" + matrixModeName(this.matrixMode)
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
                this.liveNames.add(scalar + i);
            }
            return scalar;
        }
        if (fn.ret == R_LONG) {
            // Sync object (glFenceSync): ids come from the 64-bit space.
            long sync = this.nextSync.getAndIncrement();
            this.liveSync.add(sync);
            this.liveNames.add((int) sync);
            return sync;
        }
        // Array form (glGenTextures, glCreateTextures...): the out[] is the LAST pointer arg; the count is the
        // int immediately before it.
        int outIndex = lastPointerArg(fn);
        if (outIndex < 0) {
            int scalar = RenderObjectIds.reserve(1);
            this.liveNames.add(scalar);
            return scalar;
        }
        long out = c[outIndex];
        if (out == 0L) {
            return 0L;
        }
        int n = countFromArgs(fn, c, outIndex);
        for (int i = 0; i < n; i++) {
            int name = RenderObjectIds.reserve(1);
            this.liveNames.add(name);
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
            for (int i = 0; i < count; i++) {
                int name = memGetInt(names + (long) i * 4L);
                this.liveNames.remove(name);
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
            int first = (int) c[0];
            int range = (int) c[1];
            for (int i = 0; i < range; i++) {
                Integer name = Integer.valueOf(first + i);
                this.displayLists.remove(name);
                this.liveNames.remove(name.intValue());
                releaseListPayloads(name);
            }
            this.displayListsDeleted += range;
            return;
        }
        if (fn.args.length == 1) {
            if (fn.args[0] == A_LONG) {
                this.liveSync.remove(c[0]);
            } else {
                int name = (int) c[0];
                this.liveNames.remove(name);
                if ("glDeleteBuffers".equals(fn.name)) {
                    forgetBuffer(name);
                }
            }
        }
    }

    /**
     * Retires a texture's extent and pixel records.
     * <p>
     * Names are never reissued by the generator, but the game reuses one itself: {@code TextureUtil.allocateTexture}
     * deletes the texture and immediately uploads to the same name again, mip level by mip level. A capture left
     * behind by the previous image would be read as the new texture's pixels until its first real upload replaced it -
     * stale content that looks like a working mirror, which is worse than the null an un-uploaded level answers with.
     * The extents go with the pixels: a level nobody uploaded has no size to report.
     */
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
        this.bufferStore.remove(Integer.valueOf(name));
        this.bufferLogicalSize.remove(Integer.valueOf(name));
        for (int target = 0; target < this.bufferBindings.length; target++) {
            if (this.bufferBindings[target] == name) {
                this.bufferBindings[target] = 0;
            }
        }
    }

    private void deleteTexture(int name) {
        if (name != 0) {
            Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
                for (java.util.Map.Entry<Integer, Integer> entry : this.framebufferColorTextures.entrySet()) {
                    if (entry.getValue().intValue() == name) {
                        vulkan.celtium$rawAttachmentTextureDeleted(entry.getKey().intValue());
                    }
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
        it.unimi.dsi.fastutil.longs.LongIterator bindings = this.textureBindings.keySet().iterator();
        while (bindings.hasNext()) {
            if (this.textureBindings.get(bindings.nextLong()) == name) bindings.remove();
        }
        forgetTexture(name);
    }

    /** Invalidates storage/content for replacement. Unlike deletion, allocation must preserve the current binding. */
    private void forgetTexture(int name) {
        // Parameters are recorded per texture name, and TextureUtil.allocateTexture deletes a name and re-uploads to the
        // same one. Leaving the parameters behind handed the new object the old wrap/filter (measured: WRAP_S stayed
        // CLAMP_TO_EDGE where the default is REPEAT, #389), and the records are capped, so a long session of reloads would
        // eventually stop recording new ones.
        long keySpace = ((long) name) << 32;
        it.unimi.dsi.fastutil.longs.LongIterator keys = this.texParams.keySet().iterator();
        while (keys.hasNext()) {
            long key = keys.nextLong();
            if ((key & 0xFFFFFFFF00000000L) == keySpace) {
                keys.remove();
            }
        }
        for (int level = 0; level < 256; level++) {
            long key = texLevelKey(name, level);
            this.texLevels.remove(key);
            this.texCaptures.remove(key);
            java.util.List<TexCapture> pending = this.texPatchBacklog.remove(key);
            if (pending != null) {
                for (TexCapture capture : pending) {
                    this.texPatchBytes -= capture.data.capacity();
                }
            }
        }
    }

    // ---- state queries ---------------------------------------------------------------

    /**
     * {@return the live Vulkan driver identity for {@code GL_RENDERER}}, or {@code "unavailable"} when no device
     * exists.
     * <p>
     * Resolved through {@code TerrainGpuBackends.current()} rather than cached: the window renderer is created at
     * display creation and adopted by the world renderer later, so the answer appears as soon as a device exists and
     * never goes stale across a world change. The string is the one the driver itself reports
     * ({@code VulkanContext.driverSummary()}: driverName/driverID/driverInfo/driverVersion and the device), because
     * the user's F3 request was for the Vulkan driver, not for this stub's name.
     * <p>
     * The fallback is {@code "unavailable"} and deliberately not a stub string: presenting "Celtium stub GL" where a
     * driver name is expected is how the overlay came to claim a driver that was not rendering. A configuration with
     * no device has no driver to name, and says so.
     */
    private static String celtium$nativeRendererName() {
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                String driver = org.soutalab.celtium.vulkan.VulkanContext.driverSummary();
                if (driver != null && !driver.isEmpty() && !"unavailable".equals(driver)) {
                    return driver + " via Celtium Vulkan";
                }
            }
        } catch (RuntimeException | LinkageError error) {
            // No renderer, or one that cannot answer: the caller gets "unavailable" rather than a stub name.
        }
        return "unavailable";
    }

    private long stringFor(int pname) {
        String value;
        switch (pname) {
            case 0x1F00: // GL_VENDOR
                value = "Celtium";
                break;
            case 0x1F01: // GL_RENDERER
                // The live Vulkan driver the frames are actually drawn on, not this stub's own name: F3 shows this
                // string, and the fixture records it as glRenderer. Before the renderer exists there is no device to
                // name yet, and a configuration without Vulkan has none at all - both answer "unavailable" rather
                // than a stub name standing in for a driver. GL_VENDOR stays "Celtium" so a GL-less leg remains
                // identifiable in a report.
                value = celtium$nativeRendererName();
                break;
            case 0x1F02: // GL_VERSION
                // BH-35: what is advertised is the version whose gates are all backed, enumerated rather than guessed
                // (docs/history/scope-and-limits.md, the compatibility-boundary table). The floor is 3.0, and it is a floor
                // because vanilla reads it through LWJGL's capability flags:
                //   OpenGlHelper.initializeShaders needs framebufferSupported =
                //     openGL14 && (ARB_framebuffer_object || EXT_framebuffer_object || OpenGL30); this stub advertises
                //     no extensions at all, so OpenGL30 is the only term that can make it true; and
                //     shadersAvailable = openGL21 || (ARB shaders), so OpenGL21 is needed too. OpenGL15 (VBOs) and
                //     OpenGL20 (GuiMainMenu) follow from 3.0. Advertising less than 3.0 turns shadersSupported off and
                //     with it the outline/spectator and post-process paths (EntityRenderer:195/219/290,
                //     RenderGlobal:216/565). Advertising more than 3.0 claims entry points this stub answers with
                //     recorded no-ops, which is the lie BH-35 names.
                value = "3.0.0 Celtium";
                break;
            case 0x1F03: // GL_EXTENSIONS
                // Empty on purpose, and indexed extensions answer NULL: this renderer interprets no extension, so it
                // names none. Claiming an extension whose entry points are no-ops is the same defect as claiming 4.6.
                value = "";
                break;
            case 0x8B8C: // GL_SHADING_LANGUAGE_VERSION
                // Decided separately from GL_VERSION and by the same rule: the shaders this renderer actually takes are
                // vanilla's, which are #version 120 (ShaderGroup, the outline and the post chains). Nothing in the
                // vanilla sources or the mod cohort gates on this string (Fugue's only glGetString is GL_RENDERER,
                // HEI inherits vanilla's OpenGlHelper gate, flare/scalar/LagSpikeProfiler never query a GL string), so
                // 1.20 is the highest version whose shaders are backed rather than the highest number available.
                value = "1.20";
                break;
            default:
                value = "";
        }
        return intern(value);
    }

    private long stringIndexed(int pname, int index) {
        // Only GL_EXTENSIONS answers by index - empty list, so anything beyond 0 is NULL.
        return 0L;
    }

    private long intern(String ascii) {
        // One buffer is enough: the game compares/prints, it does not keep the pointer. Grow it only when the
        // string is larger than what is already interned.
        byte[] bytes = (ascii + "\0").getBytes(StandardCharsets.US_ASCII);
        if (this.getStringBuffer == null || this.getStringBuffer.capacity() < bytes.length) {
            this.getStringBuffer = ByteBuffer.allocateDirect(bytes.length);
            this.getStringAddress = memAddress(this.getStringBuffer);
        }
        this.getStringBuffer.clear();
        this.getStringBuffer.put(bytes);
        return this.getStringAddress;
    }

    /**
     * {@return the stub's answer to {@code glGetInteger(pname)} for the tracked scalar parameters}, or
     * {@link Integer#MIN_VALUE} when the pname is array-valued or untracked - the same values {@link #getInts} writes,
     * exposed so a caller inside the JVM can ask the stub directly instead of paying a trampoline round trip for a
     * field it already holds. The fn-census measurement that motivated it: 72% of all glGetIntegerv arrivals were
     * GL_TEXTURE_BINDING_2D, most of them this project's own capture and mirror code re-reading state the stub tracks.
     */
    public static int celtium$getInteger(int pname) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return Integer.MIN_VALUE;
        }
        return stub.getIntegerValue(pname);
    }

    /** The scalar half of {@link #getInts}; {@link Integer#MIN_VALUE} means "not a tracked scalar pname". */
    private int getIntegerValue(int pname) {
        switch (pname) {
            case 0x0BE1: // GL_BLEND_SRC
            case 0x80C9: return this.raster.blendSrcRgb(); // GL_BLEND_SRC_RGB
            case 0x0BE0: // GL_BLEND_DST
            case 0x80C8: return this.raster.blendDstRgb(); // GL_BLEND_DST_RGB
            case 0x80CB: return this.raster.blendSrcAlpha();
            case 0x80CA: return this.raster.blendDstAlpha();
            case 0x8009: return this.raster.blendEquationRgb();
            case 0x883D: return this.raster.blendEquationAlpha();
            case 0x0B45: return this.raster.cullFace();
            case 0x0B46: return this.raster.frontFace();

            case 0x8894: // GL_ARRAY_BUFFER_BINDING, including a native API resource's opaque identity
                return NativeGeometryBuffer.boundId();
            case 0x8069: { // GL_TEXTURE_BINDING_2D - the name bound to TEXTURE_2D on the active unit
                return this.textures.texture2d();
            }
            case 0x84E0: // GL_ACTIVE_TEXTURE
                return 0x84C0 + this.textures.activeUnit();
            case 0x8CA6: // GL_DRAW_FRAMEBUFFER_BINDING / GL_FRAMEBUFFER_BINDING
                return this.drawFbo;
            case 0x8CA5: // GL_READ_FRAMEBUFFER_BINDING
                return this.readFbo;
            case 0x0B33: // GL_LIST_INDEX - the display list being compiled, which the model path keys on
                return this.compilingList;
            case 0x8B8D: // GL_CURRENT_PROGRAM
                return this.currentProgram;
            case 0x0B74: // GL_DEPTH_FUNC
                return 0x0201; // GL_LESS
            case 0x0D05: // GL_PACK_ALIGNMENT
                return 4;
            case 0x0B65: // GL_FOG_MODE
                return org.soutalab.celtium.vulkan.ImmediateFogState.current().glMode();
            case 0x85B5: // GL_VERTEX_ARRAY_BINDING
                return this.boundVertexArray;
            case 0x0D33: // GL_MAX_TEXTURE_SIZE
                return 16384;
            case 0x8869: // GL_MAX_VERTEX_ATTRIBS
                return 16;
            case 0x821B: // GL_MAJOR_VERSION
                // Kept consistent with the version string this stub advertises ("3.0.0 Celtium", see the
                // GL_VERSION case below). The two used to disagree - the string said 3.0 while these said 4.6 -
                // and the disagreement was not harmless: MappedStagingBuffer.isSupported asks
                // LWJGL.isOpenGLVersionSupported(4, 4), got true from these numbers, and took the persistent
                // mapping path, whose constructor maps 16 MB through commandList.mapBuffer. Measured
                // 2026-10-15 (leg gv-cohort-fix151): the client stalls inside that construction, in
                // RenderRegionManager's createStagingBuffer, with the world loaded and no player ever created.
                // A capability claim has to be one story: with 3.0 the support test is false and the fallback
                // staging buffer is used instead, which creates a plain mutable buffer.
                return 3;
            case 0x821C: // GL_MINOR_VERSION
                return 0;
            case 0x821D: // GL_NUM_EXTENSIONS
                return 0;
            case 0x8513: // GL_TEXTURE_MAX_ANISOTROPY samples? actually MAX_TEXTURE_MAX_ANISOTROPY is 0x84FF
                return 16;
            case 0x84FF:
                return 16;
            case 0x8B4D: // GL_MAX_TEXTURE_IMAGE_UNITS
                return 16;
            default:
                return Integer.MIN_VALUE;
        }
    }

    /**
     * Answers tracked integer state for LWJGL3's replaced native query. Untracked capability limits are
     * left to the bootstrap helper, which can also answer them before this provider is installed.
     */
    public static boolean celtium$getIntegers(int pname, long out) {
        CeltiumGlStub stub = installed;
        if (stub == null || out == 0L) {
            return false;
        }
        if (pname != 0x0BA2 && pname != 0x0C10 && stub.getIntegerValue(pname) == Integer.MIN_VALUE) {
            return false;
        }
        stub.getInts(pname, out, null);
        return true;
    }

    private void getInts(int pname, long out, Fn fn) {
        if (out == 0L) {
            return;
        }
        int value;
        switch (pname) {
            case 0x0BA2: // GL_VIEWPORT
                memPutInt(out, this.viewportX);
                memPutInt(out + 4, this.viewportY);
                memPutInt(out + 8, this.viewportW);
                memPutInt(out + 12, this.viewportH);
                return;
            case 0x0C10: // GL_SCISSOR_BOX - independent of the viewport, even when scissor is disabled
                memPutInt(out, this.raster.scissorX());
                memPutInt(out + 4, this.raster.scissorY());
                memPutInt(out + 8, this.raster.scissorWidth());
                memPutInt(out + 12, this.raster.scissorHeight());
                return;
            case 0x8872: // GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS
                value = 32;
                break;
            case 0x0B33: // GL_LIST_INDEX - the list glNewList opened, 0 when none is open
                // This is the answer the model capture reads to decide whether a quad belongs to a list. It asked the
                // driver and got 0, refused the quad with `reason=unknown-list`, and nothing drew the model - so the
                // recording side of display lists was never reached even after the lists were implemented. The stub
                // already tracked the open list; it simply did not answer the question about it.
                value = this.compilingList;
                break;
            case 0x0B30: // GL_LIST_MODE - GL_COMPILE (0x1300) or GL_COMPILE_AND_EXECUTE (0x1301); 0 when none is open
                value = this.compilingList == 0 ? 0 : this.compilingListMode;
                break;
            default:
                value = getIntegerValue(pname);
                if (value == Integer.MIN_VALUE) {
                    value = 0;
                }
        }
        memPutInt(out, value);
    }

    private void getLongs(int pname, long out, Fn fn) {
        if (out != 0L) {
            memPutLong(out, 0L);
        }
    }

    /**
     * Answers a float query with the number of components its pname has.
     * <p>
     * One float was written for every pname before this, which left the rest of the caller's buffer uninitialised: a
     * sixteen-float matrix read produced one zero and fifteen words of whatever the allocation held, and every
     * transform built from it was garbage. Matrices read the renderer's live state mirror (identity only before a
     * backend exists); scalar and vector queries write exactly the number of components implied by their pname.
     */
    private void getFloats(int pname, long out, Fn fn) {
        getFloats(pname, out, "glGetDoublev".equals(fn.name));
    }

    /** The same state-query implementation for callback dispatch and LWJGL3's replaced native entry points. */
    public static void celtium$getFloats(int pname, long out, boolean asDouble) {
        if (out == 0L) {
            return;
        }
        CeltiumGlStub stub = installed;
        if (stub != null) {
            stub.getFloats(pname, out, asDouble);
        } else {
            putFloatOrDouble(out, 0, 0.0f, asDouble);
        }
    }

    private void getFloats(int pname, long out, boolean asDouble) {
        org.soutalab.celtium.vulkan.ImmediateFogState fog = org.soutalab.celtium.vulkan.ImmediateFogState.current();
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
        switch (pname) {
            case 0x0BA6: // GL_MODELVIEW_MATRIX
            case 0x0BA7: // GL_PROJECTION_MATRIX
            case 0x0BA8: // GL_TEXTURE_MATRIX
                // The renderer's own mirror when it has one. Vanilla reads the terrain's projection and model-view
                // through exactly this call (ActiveRenderInfo.updateRenderInfo -> GlStateManager.getFloat), and it
                // builds the frustum the chunk traversal culls with from the same two reads (ClippingHelperImpl). An
                // identity here therefore does two things at once: every section but the camera's own is culled, and
                // the surviving geometry is drawn with clip coordinates equal to camera-relative world coordinates -
                // measured as 1-2 sections instead of 13-17, and as a two-block window of stretched atlas instead of a
                // terrain plane.
                float[] mirrored = mirroredMatrix(pname);
                for (int i = 0; i < 16; i++) {
                    putFloatOrDouble(out, i, mirrored != null ? mirrored[i] : (i % 5 == 0 ? 1.0f : 0.0f), asDouble);
                }
                return;
            case 0x0B00: // GL_CURRENT_COLOR
                putFloatOrDouble(out, 0, this.currentRed, asDouble);
                putFloatOrDouble(out, 1, this.currentGreen, asDouble);
                putFloatOrDouble(out, 2, this.currentBlue, asDouble);
                putFloatOrDouble(out, 3, this.currentAlpha, asDouble);
                return;
            case 0x0B70: // GL_DEPTH_RANGE
                putFloatOrDouble(out, 0, 0.0f, asDouble);
                putFloatOrDouble(out, 1, 1.0f, asDouble);
                return;
            case 0x0B21: // GL_LINE_WIDTH
                putFloatOrDouble(out, 0, this.lineWidth, asDouble);
                return;
            case 0x0B22: // GL_POINT_SIZE
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                return;
            case 0x0D3A: // GL_ALIASED_LINE_WIDTH_RANGE
                putFloatOrDouble(out, 0, 1.0f, asDouble);
                putFloatOrDouble(out, 1, this.lineWidth, asDouble);
                return;
            default:
                putFloatOrDouble(out, 0, 0.0f, asDouble);
        }
    }

    /**
     * The matrix the renderer's mirror holds for a GL matrix pname, or null when no mirror is published.
     * <p>
     * The stub is the only thing that can answer these reads, and answering the identity is not a neutral choice -
     * it is a culling frustum and a camera transform. The backend publishes what its own hooks observed, so the two
     * agree on what the frame's matrices are.
     */
    private static float[] mirroredMatrix(int pname) {
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (!(backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend)) {
            return null;
        }
        org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan =
                (org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) backend;
        // Peeked, not copied: the caller writes the sixteen values out at once, and vanilla asks per entity.
        switch (pname) {
            case 0x0BA6:
                return vulkan.celtium$peekMirroredMatrix(org.soutalab.celtium.vulkan.VulkanGlState.MATRIX_MODE_MODELVIEW);
            case 0x0BA7:
                return vulkan.celtium$peekMirroredMatrix(org.soutalab.celtium.vulkan.VulkanGlState.MATRIX_MODE_PROJECTION);
            case 0x0BA8:
                return vulkan.celtium$peekMirroredMatrix(org.soutalab.celtium.vulkan.VulkanGlState.MATRIX_MODE_TEXTURE);
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

    private void getBooleans(int pname, long out, Fn fn) {
        if (out == 0L) {
            return;
        }
        // The query used to write a single false byte and nothing else, whatever was asked. Callers that save and restore
        // GL state read the whole vector: glColorMask(true,true,true,true) is recorded here, and the readback answered
        // [0,85,85,85] because only the first byte was written and it was false (#388).
        switch (pname) {
            case 0x0C23: { // GL_COLOR_WRITEMASK
                int mask = this.raster.colorMask();
                for (int i = 0; i < 4; i++) {
                    MemoryUtil.memPutByte(out + i, (byte) (((mask >>> i) & 1) != 0 ? 1 : 0));
                }
                return;
            }
            case 0x0B72: { // GL_DEPTH_WRITEMASK
                // GL's default is enabled and glDepthMask sets the same value the pipeline reads from the enable record.
                MemoryUtil.memPutByte(out, (byte) (enabledCap(GL_DEPTH_WRITEMASK_CAP) ? 1 : 0));
                return;
            }
            case 0x0BD0: { // GL_DEPTH_TEST
                MemoryUtil.memPutByte(out, (byte) (enabledCap(GL_DEPTH_TEST) ? 1 : 0));
                return;
            }
            case 0x0BE2: { // GL_BLEND
                MemoryUtil.memPutByte(out, (byte) (enabledCap(GL_BLEND) ? 1 : 0));
                return;
            }
            case 0x0B90: { // GL_STENCIL_TEST
                MemoryUtil.memPutByte(out, (byte) (enabledCap(0x0B90) ? 1 : 0));
                return;
            }
            case 0x0B71: { // GL_DEPTH_WRITEMASK's sibling for the whole depth test is above; this is GL_SCISSOR_TEST
                MemoryUtil.memPutByte(out, (byte) (enabledCap(GL_SCISSOR_TEST) ? 1 : 0));
                return;
            }
            case 0x0B44: { // GL_CULL_FACE
                MemoryUtil.memPutByte(out, (byte) (enabledCap(GL_CULL_FACE) ? 1 : 0));
                return;
            }
            case 0x0BC0: { // GL_ALPHA_TEST
                MemoryUtil.memPutByte(out, (byte) (enabledCap(0x3000) ? 1 : 0));
                return;
            }
            case 0x0DE1: { // GL_TEXTURE_2D
                MemoryUtil.memPutByte(out, (byte) (enabledCap(GL_TEXTURE_2D) ? 1 : 0));
                return;
            }
            default:
                // A query this stub has no record for: the spec says the value is undefined, and writing false over the
                // caller's whole destination would destroy whatever it had laid out next. One byte stays, as before.
                MemoryUtil.memPutByte(out, (byte) 0);
        }
    }

    /** The index the stub uses in {@link #enables} for {@code glDepthMask}, which GL has no capability for. */
    private static final int GL_DEPTH_WRITEMASK_CAP = 0x0B72;

    /** {@return whether a capability this stub records is enabled}. */
    private boolean enabledCap(int cap) {
        switch (cap) {
            case GL_FOG: return org.soutalab.celtium.vulkan.ImmediateFogState.current().enabled();
            case GL_BLEND: return this.raster.blendEnabled();
            case GL_CULL_FACE: return this.raster.cullEnabled();
            case GL_SCISSOR_TEST: return this.raster.scissorTest();
            case GL_POLYGON_OFFSET_FILL: return this.raster.polygonOffsetFillEnabled();
            case GL_TEXTURE_2D: return this.textures.enabled2d(this.textures.activeUnit());
            default: break;
        }
        return cap >= 0 && cap < this.enables.length && this.enables[cap] != 0;
    }

    /** Pixel/semantic routing - the layer the texture path plugs into. Nothing routes yet: calls no-op. */
    private void route(Fn fn, long[] c) {
        // Intentionally empty.
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
    private final java.util.Map<Integer, Integer> framebufferColorTextures =
            new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();

    /** Reverse lookup for texture sampling: avoid a scan through every Mod FBO on each draw. */
    private final java.util.Map<Integer, Integer> colorTextureFramebuffers =
            new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();

    /** OpenGL texture 0 detaches GL_COLOR_ATTACHMENT0, never leaves its previous image alive. */
    static void updateColorAttachment(java.util.Map<Integer, Integer> forward,
            java.util.Map<Integer, Integer> reverse, int framebuffer, int texture) {
        if (framebuffer == 0) return;
        Integer previous = forward.remove(Integer.valueOf(framebuffer));
        if (previous != null) reverse.remove(previous, Integer.valueOf(framebuffer));
        if (texture != 0) {
            forward.put(Integer.valueOf(framebuffer), Integer.valueOf(texture));
            reverse.put(Integer.valueOf(texture), Integer.valueOf(framebuffer));
        }
    }

    private void forgetFramebuffer(int name) {
        if (name == 0) return;
        Integer color = this.framebufferColorTextures.get(Integer.valueOf(name));
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            vulkan.celtium$rawFramebufferDeleted(name, color == null ? 0 : color.intValue());
        }
        updateColorAttachment(this.framebufferColorTextures, this.colorTextureFramebuffers, name, 0);
        if (this.drawFbo == name) {
            this.drawFbo = 0;
            this.framebufferBindSerial++;
        }
        if (this.readFbo == name) this.readFbo = 0;
    }

    /** The attachment record {@code glFramebufferTexture2D} writes and the backend reads. */
    private void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        int framebuffer = target == 0x8CA8 ? this.readFbo : this.drawFbo;
        if (framebuffer == 0 || level != 0) {
            return;
        }
        // Only the colour attachment: depth and stencil attachments are not sampled as colour, and GL_COLOR_ATTACHMENT0
        // is 0x8CE0 - the attachment a render target's pixels come from.
        if ((attachment & 0xFFFF) != 0x8CE0) {
            return;
        }
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            vulkan.celtium$rawAttachmentChanged(framebuffer);
        }
        updateColorAttachment(this.framebufferColorTextures, this.colorTextureFramebuffers, framebuffer, texture);
        if (texture == 0) return;
        if (framebufferAttachmentLogs < 8) {
            framebufferAttachmentLogs++;
            System.out.println("[Celtium] CELTIUM_RAW_FBO framebuffer=" + framebuffer + " colorTexture=" + texture
                    + " textarget=0x" + Integer.toHexString(textarget) + " level=" + level
                    + " colorAttachments=" + this.framebufferColorTextures.size());
        }
    }

    /** Bounded, so a mod that rebuilds its framebuffer every frame cannot flood the log. */
    private static int framebufferAttachmentLogs;

    /** {@return a texture's level-0 width and height as GL last defined them}, or null when nothing was recorded. */
    public static int[] celtium$textureExtent(int glTextureName) {
        CeltiumGlStub stub = installed;
        if (stub == null || glTextureName == 0) {
            return null;
        }
        int[] level = stub.texLevels.get(texLevelKey(glTextureName, 0));
        return level == null ? null : new int[] {level[0], level[1]};
    }

    /** {@return the colour texture of a raw framebuffer, or 0 when none was recorded}. */
    public static int celtium$framebufferColorTexture(int glFramebufferName) {
        CeltiumGlStub stub = installed;
        if (stub == null || glFramebufferName == 0) {
            return 0;
        }
        Integer texture = stub.framebufferColorTextures.get(Integer.valueOf(glFramebufferName));
        return texture == null ? 0 : texture.intValue();
    }

    /** {@return the framebuffer a colour texture belongs to, or 0 when the texture is not a raw attachment}. */
    public static int celtium$framebufferForColorTexture(int glTextureName) {
        CeltiumGlStub stub = installed;
        if (stub == null || glTextureName == 0) {
            return 0;
        }
        Integer cached = stub.colorTextureFramebuffers.get(Integer.valueOf(glTextureName));
        if (cached != null && Integer.valueOf(glTextureName).equals(
                stub.framebufferColorTextures.get(cached))) return cached.intValue();
        stub.colorTextureFramebuffers.remove(Integer.valueOf(glTextureName));
        for (java.util.Map.Entry<Integer, Integer> entry : stub.framebufferColorTextures.entrySet()) {
            if (entry.getValue().intValue() == glTextureName) {
                stub.colorTextureFramebuffers.put(Integer.valueOf(glTextureName), entry.getKey());
                return entry.getKey().intValue();
            }
        }
        return 0;
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
        if (capability < 0 || capability >= this.enables.length) {
            return;
        }
        byte next = (byte) (enabled ? 1 : 0);
        switch (capability) {
            case GL_FOG: org.soutalab.celtium.vulkan.ImmediateFogState.current().enabled(enabled); break;
            case GL_BLEND: this.raster.blendEnabled(enabled); break;
            case GL_CULL_FACE: this.raster.cullEnabled(enabled); break;
            case GL_SCISSOR_TEST: this.raster.scissorTest(enabled); break;
            case GL_POLYGON_OFFSET_FILL: this.raster.polygonOffsetFillEnabled(enabled); break;
            case GL_TEXTURE_2D: this.textures.enabled2d(enabled); break;
            default:
                if (this.enables[capability] != next && isDrawStateCapability(capability)) noteDrawStateBoundary();
                break;
        }
        this.enables[capability] = next;
        this.capabilityObserved[capability] = true;
    }

    private static boolean isProxyTarget(int target) {
        // GL_PROXY_TEXTURE_1D/2D/3D/CUBE_MAP - uploads to these carry no pixels and bind to nothing.
        return target == 0x8063 || target == 0x8064 || target == 0x8070 || target == 0x851B;
    }

    private long texBindKey(int target) {
        return ((long) this.textures.activeUnit() << 16) | (target & 0xFFFFL);
    }

    /**
     * The shared record key: texture name in the high bits, level in the low byte. {@link #texLevels} and
     * {@link #texCaptures} agree on it, so a level's extent and its pixels are found the same way.
     */
    private static long texLevelKey(int name, int level) {
        return ((long) name << 16) | (level & 0xFFL);
    }

    /**
     * The name an upload's records belong to: the texture bound to the target on the active unit, or {@code -target}
     * for a proxy target. Proxy uploads are how FML probes the maximum texture size - they bind to nothing and their
     * "pixels" are never a real image, so only an extent is recorded for them.
     */
    private int uploadTargetName(int target) {
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
    private void recordExtent(int target, int level, int internal, int width, int height) {
        int name = uploadTargetName(target);
        if (name == 0) {
            return;
        }
        int[] previous = this.texLevels.put(texLevelKey(name, level), new int[] { width, height, internal });
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
        int name = bufferFor(fn, c);
        long offset = c[1];
        long size = c[2];
        long data = c[3];
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
        if (nativeBuffer != null) {
            if (data != 0L && size > 0 && offset >= 0 && size <= nativeBuffer.byteSize()
                    && offset <= nativeBuffer.byteSize() - size) {
                nativeBuffer.write(offset, MemoryUtil.memByteBuffer(data, (int) size));
            }
            return;
        }
        ByteBuffer storage = this.bufferStore.get(name);
        int logicalSize = this.logicalSizeOf(name, storage == null ? 0 : storage.capacity());
        if (storage == null || data == 0L || size <= 0 || offset < 0 || offset + size > logicalSize) {
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
        switch (pname) {
            case 0x0CF5: // GL_UNPACK_ALIGNMENT - 1, 2, 4 or 8; anything else is not a legal GL value
                this.unpackAlignment = value == 1 || value == 2 || value == 8 ? value : 4;
                break;
            case 0x0CF2: // GL_UNPACK_ROW_LENGTH
                this.unpackRowLength = Math.max(0, value);
                break;
            case 0x0CF3: // GL_UNPACK_SKIP_ROWS
                this.unpackSkipRows = Math.max(0, value);
                break;
            case 0x0CF4: // GL_UNPACK_SKIP_PIXELS
                this.unpackSkipPixels = Math.max(0, value);
                break;
            default:
                break;
        }
    }

    // ---- texture pixels: the uploads the Vulkan mirrors are built from -----------------------------------

    /** The pixel formats and types the conversion understands - the pairs the game's texture loader uses. */
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_RGB = 0x1907;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_BGRA = 0x80E1;
    private static final int GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367;
    private static final int GL_UNSIGNED_INT_8_8_8_8 = 0x8035;

    /**
     * The largest level worth mirroring: 4096x4096 RGBA8, the biggest atlas the game builds. A larger upload is
     * counted as unconverted rather than allocated - one level of a speculative 16384x16384 probe would be a gigabyte
     * of direct memory for pixels the game itself never had to find room for.
     */
    private static final long MAX_CAPTURE_BYTES = 64L * 1024L * 1024L;
    /** Per-key patch-write census: which (name,level) the captured sub-image writes actually land on. */
    private final java.util.Map<Long, Integer> patchKeys = new java.util.HashMap<Long, Integer>();
    /** Which texSubImage branch each patch took, for the capture-layout question. */
    private final java.util.Map<String, Integer> patchBranches = new java.util.HashMap<String, Integer>();
    /**
     * Patches a level could not hold at full extent - the budget above - stay in a per-level backlog, which the
     * mirror path replays as region writes. This is how a big atlas fills at all: its sprite uploads are raw
     * {@code glTexSubImage2D} calls that carry no announcement (measured: 37,713 captured patches vs ~1,182
     * announced fills in one E2E run), so without the backlog the mirror covered only the announced fraction.
     * Bounded because the texture may never be mirrored; the atlas's whole stitch burst measured ~143 MB.
     */
    // Primitive-keyed: textureUploadSerial reads it on every offered batch, and a boxed Long key was an allocation and
    // a Long.equals per offer.
    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<java.util.List<TexCapture>> texPatchBacklog =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<java.util.List<TexCapture>>();
    private long texPatchBytes;
    private static final long MAX_PATCH_BACKLOG_BYTES = 192L * 1024L * 1024L;

    /** One captured level: RGBA8, {@code width * height} pixels, top row first, in stub-owned direct storage. */
    private static final class TexCapture {
        final int width;
        final int height;
        final ByteBuffer data;
        /**
         * Whether the game itself wrote a pixel into this level - true for a {@code glTexImage2D} that carried data and
         * for a {@code glTexSubImage2D} patch, false for the zero-filled surface {@link #undefinedLevel} lays down for
         * an allocation with a NULL data pointer.
         * <p>
         * The surface alone cannot answer this: an undefined level holds zeros whether the game filled it or not, and a
         * mirror built from those zeros claims whole content that no upload ever put there. Consumers ask through
         * {@link #textureLevelHasGamePixels}. Mutable because a patch is copied into an already filed surface, and
         * volatile because the game thread writes it while a reader may be polling the record.
         */
        volatile boolean gamePixels;
        /**
         * The write number the level reached when this record's bytes became true.
         * <p>
         * Every write the stub files - a full image, an undefined level, a patch - takes the next number from
         * {@link CeltiumGlStub#texUploadSerial}; a record is assigned its number at construction and an in-place patch
         * bumps the number on the record it rewrote. A consumer that holds the number it last mirrored can answer "did
         * the game touch this level since" in O(1) - the alternative the mirrors used before was an FNV pass over
         * every byte of the level on every ask, which is what the per-batch light-map upload measured against.
         * Volatile for the same reason {@link #gamePixels} is.
         */
        volatile long serial;
        /**
         * The texel offset this record covers inside its level, for a capture that is a {@code glTexSubImage2D}
         * patch rather than the whole level. Zero on every record that fills its level from the origin; the patch
         * case exists because a level too large for the capture budget keeps its patch at patch size instead of an
         * extent-sized surface - the mirror path writes it at this position, not at 0,0.
         */
        final int x;
        final int y;

        TexCapture(int width, int height, ByteBuffer data, boolean gamePixels, long serial) {
            this(width, height, data, gamePixels, serial, 0, 0);
        }

        TexCapture(int width, int height, ByteBuffer data, boolean gamePixels, long serial, int x, int y) {
            this.width = width;
            this.height = height;
            this.data = data;
            this.gamePixels = gamePixels;
            this.serial = serial;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * {@code glTexImage1D/2D/3D}: the extent record plus the uploaded pixels, converted to RGBA8 and filed under the
     * bound texture's level. A NULL data pointer is a legal GL call that allocates the level with undefined content -
     * see {@link #undefinedLevel}, because the game fills levels exactly that way.
     */
    private void texImagePixels(Fn fn, long[] c) {
        texImagePixels(fn.name, c);
    }

    private void texImagePixels(String name, long[] c) {
        texImagePixels(name, (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], (int) c[5], (int) c[6],
                (int) c[7], c[8]);
    }

    /**
     * The one tex-image producer: the dispatch case and the transformer's route both land here.
     *
     * <p>Routing {@code glTexImage2D}/{@code glTexSubImage2D} matters because the stub's capture is what gives a
     * texture its pixels under GL-less. Measured on the 268-mod E2E pack: the block atlas (GL name 353) never reached
     * the mirror and the registry logged {@code CELTIUM_CAPTURE_MISS glTexture=353 level=0 ... the stub captured no
     * upload for this texture level}, so the atlas stayed incomplete, the terrain pass refused its batches, and the
     * lower half of the frame was drawn by nobody. The census counted the calls that were dropped all along:
     * {@code nglTexSubImage2D=41199} in one cohort run.
     */
    private void texImagePixels(String name, int target, int level, int internal, int width, int height, int border,
            int format, int type, long pixels) {
        boolean oneD = "glTexImage1D".equals(name);
        boolean threeD = "glTexImage3D".equals(name);
        int extentHeight = oneD ? 1 : height;
        int depth = threeD ? height : 1;
        // The format slot follows the extent, which differs per form: 1D has no height, 3D carries depth. The
        // transformer's route only carries the 2D form, whose slots are the ones this method's parameters name.
        recordExtent(target, level, internal, width, extentHeight);
        int targetName = uploadTargetName(target);
        if (isProxyTarget(target) || targetName == 0) {
            return;
        }
        if (!carriesPixels(pixels, this.pixelUnpackBuffer)) {
            undefinedLevel(targetName, level, width, extentHeight, depth);
            return;
        }
        if (depth > 1) {
            // A 2D mirror cannot hold the remaining slices: the extent is recorded and the upload counted rather than
            // storing the first slice as if it were the whole image.
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        ByteBuffer rgba = uploadToRgba8(width, height, format, type, pixels);
        if (rgba == null) {
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        // Publishing a new record instead of rewriting the old one, so a reader polling the level sees one whole image.
        this.texCaptures.put(texLevelKey(targetName, level),
                new TexCapture(width, extentHeight, rgba, true, this.texUploadSerial.incrementAndGet()));
        this.fullCaptures.incrementAndGet();
    }

    /**
     * {@return whether a texture upload's {@code pixels} argument names data}. With a pixel unpack buffer bound it is an
     * offset into that buffer, and offset 0 is its first byte - not "no data". Xaero's Minimap uploads every map tile
     * that way ({@code glTexSubImage2D(..., 0L)} from a PBO), and reading 0 as NULL dropped all of them: the minimap
     * showed the world through itself, every tile batch refused for want of a complete image.
     */
    static boolean carriesPixels(long pixels, int unpackBuffer) {
        return pixels != 0L || unpackBuffer > 0;
    }

    /**
     * A level allocated with a NULL data pointer: GL reserves the storage and leaves its content undefined.
     * <p>
     * The level still needs a surface here, because the game fills levels in patches: {@code TextureUtil} allocates
     * every mip level with no data and then writes the image sprite by sprite through {@code glTexSubImage2D}. Without
     * one, those sprites were dropped one by one - the first became a capture the size of a single sprite and every
     * later one fell outside it, which left the block atlas mirrored as one 16x16 corner. Zeros are the honest reading
     * of an undefined level, and they are exactly what the extent record already claims, so the two records agree. It
     * carries no game bytes, so it is counted apart from the full images.
     */
    private void undefinedLevel(int name, int level, int width, int height, int depth) {
        long pixels = (long) width * (long) height;
        if (depth > 1 || width <= 0 || height <= 0 || pixels > MAX_CAPTURE_BYTES / 4L) {
            // No shape to lay a surface out from: a 2D record cannot describe depth, and this level is past the size
            // the stub will mirror at all. The extent stands, the pixels do not.
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        long bytes = pixels * 4L;
        ByteBuffer zeros = ByteBuffer.allocateDirect((int) bytes);
        // allocateDirect promises storage, not zeroed storage: HotSpot can hand back recycled memory for a small
        // buffer, and a garbage surface is exactly the silently wrong atlas this path exists to prevent.
        MemoryUtil.memSet(memAddress(zeros), 0, bytes);
        this.texCaptures.put(texLevelKey(name, level),
                new TexCapture(width, height, zeros, false, this.texUploadSerial.incrementAndGet()));
        this.undefinedExtents.incrementAndGet();
    }

    /**
     * {@code glTexSubImage2D}: the patch is converted on its own and written into the level's captured rows.
     * <p>
     * A level with no capture yet takes the patch as its storage, placed at the origin - the summary counts that as a
     * patch, not as a full image. A patch that does not fit inside the captured image is counted and dropped, the way
     * GL raises GL_INVALID_VALUE and leaves the level alone; a partly written image would be worse than none.
     */
    private void texSubImage(long[] c) {
        texSubImage((int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], (int) c[5], (int) c[6], (int) c[7],
                c[8]);
    }

    /** The mean byte value of the bytes an upload is about to read from a pixel unpack buffer, for the PBO log line. */
    private static String celtium$pboMean(ByteBuffer storage, long offset, long bytes) {
        if (storage == null || offset < 0L || offset + bytes > storage.capacity() || bytes <= 0L) {
            return "n/a";
        }
        long sum = 0L;
        long nonZero = 0L;
        for (long i = 0; i < bytes; i++) {
            int value = storage.get((int) (offset + i)) & 0xFF;
            sum += value;
            if (value != 0) nonZero++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f nonZero=%d/%d", (double) sum / bytes, nonZero, bytes);
    }

    /** The one tex-sub-image producer: the dispatch case and the transformer's route both land here. */
    private void texSubImage(int target, int level, int xOffset, int yOffset, int width, int height, int format,
            int type, long pixels) {
        if (isProxyTarget(target)) {
            return;
        }
        int x = xOffset;
        int y = yOffset;
        int name = uploadTargetName(target);
        if (this.pixelUnpackBuffer > 0 && this.pboUploadLogs < 8) {
            this.pboUploadLogs++;
            ByteBuffer storage = this.bufferStore.get(this.pixelUnpackBuffer);
            System.out.println("[Celtium] CELTIUM_PBO_UPLOAD texture=" + name + " level=" + level + " at=" + x + ","
                    + y + " size=" + width + "x" + height + " format=0x" + Integer.toHexString(format) + " type=0x"
                    + Integer.toHexString(type) + " buffer=" + this.pixelUnpackBuffer + " offset=" + pixels
                    + " storage=" + (storage == null ? "none" : storage.capacity() + "B")
                    + " sourceMean=" + celtium$pboMean(storage, pixels, (long) width * height * 4L));
        }
        if (name == 0 || !carriesPixels(pixels, this.pixelUnpackBuffer) || width <= 0 || height <= 0) {
            return;
        }
        ByteBuffer patch = uploadToRgba8(width, height, format, type, pixels);
        if (patch == null) {
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        long key = texLevelKey(name, level);
        this.patchKeys.merge(Long.valueOf(key), Integer.valueOf(1), Integer::sum);
        TexCapture existing = this.texCaptures.get(key);
        if (existing == null) {
            // A patch into a level whose size is already recorded: filed at patch size it would pose as the whole
            // level - measured on the E2E pack: the 8192x8192 atlas (GL 353) got a 1125x1125 capture from its first
            // animated sprite, the mirror was created at that size, and every later sprite was refused "outside the
            // mirror". Lay an extent-sized undefined surface (same rule as undefinedLevel) and let the normal patch
            // path write inside it; over the capture budget the patch is dropped and the mirror comes from the
            // announcement path, exactly as before the route existed.
            int[] extent = this.texLevels.get(key);
            if (extent != null && extent[0] > 0 && extent[1] > 0
                    && (x != 0 || y != 0 || width != extent[0] || height != extent[1])) {
                long pixels2 = (long) extent[0] * extent[1];
                if (pixels2 > MAX_CAPTURE_BYTES / 4L) {
                    this.patchBranches.merge("backlog", Integer.valueOf(1), Integer::sum);
                    // Over the capture budget the patch goes to the per-level backlog instead of posing as the
                    // whole level - the case that built the E2E atlas's mirror at the first sprite's 1125x1125 and
                    // refused every later patch "outside the mirror". The mirror path replays the backlog as
                    // region writes; the byte bound keeps a never-mirrored texture from growing without limit.
                    if (this.texPatchBytes + patch.remaining() > MAX_PATCH_BACKLOG_BYTES) {
                        this.unconvertedUploads.incrementAndGet();
                        return;
                    }
                    java.util.List<TexCapture> pending = this.texPatchBacklog.get(key);
                    if (pending == null) {
                        pending = new java.util.ArrayList<TexCapture>();
                        this.texPatchBacklog.put(key, pending);
                    }
                    pending.add(new TexCapture(width, height, patch, true,
                            this.texUploadSerial.incrementAndGet(), x, y));
                    this.texPatchBytes += patch.remaining();
                    this.subCaptures.incrementAndGet();
                    return;
                }
                ByteBuffer surface = ByteBuffer.allocateDirect((int) (pixels2 * 4L));
                MemoryUtil.memSet(memAddress(surface), 0, pixels2 * 4L);
                existing = new TexCapture(extent[0], extent[1], surface, false,
                        this.texUploadSerial.incrementAndGet());
                this.texCaptures.put(key, existing);
                this.undefinedExtents.incrementAndGet();
                this.patchBranches.merge("surface", Integer.valueOf(1), Integer::sum);
            }
        }
        if (existing == null) {
            // Over the capture budget, or no extent recorded at all: keep the patch as a patch-sized capture with
            // its offset, so the mirror path can write it into the extent-sized image as a region instead of
            // treating the patch as the whole level.
            this.texCaptures.put(key,
                    new TexCapture(width, height, patch, true, this.texUploadSerial.incrementAndGet(), x, y));
            this.subCaptures.incrementAndGet();
            this.patchBranches.merge("lone", Integer.valueOf(1), Integer::sum);
            return;
        }
        // A capture may itself be a patch at an offset (the over-budget path above files one at its own x,y), so
        // the position this patch writes inside the record is the level position minus the record's origin.
        int dstX = x - existing.x;
        int dstY = y - existing.y;
        this.patchBranches.merge("paste", Integer.valueOf(1), Integer::sum);
        if (dstX < 0 || dstY < 0 || dstX + width > existing.width || dstY + height > existing.height) {
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        int destinationStride = existing.width * 4;
        int sourceStride = width * 4;
        // OPT-21: an upload that does not change the pixels must not change the serial either - the serial is
        // what makes the GPU side re-supply, and a re-supply of identical bytes is pure cost. The row compare
        // runs on the same absolute positions the copy below uses, so "identical" is exactly what GL itself
        // would conclude from the pair (existing surface, this patch). The gamePixels guard keeps an
        // all-zero first patch on an undefined surface from being "identical" - GL did write pixels there,
        // and the flag/serial are what downstream readers use to tell the surface is defined.
        boolean identical = existing.gamePixels;
        for (int row = 0; identical && row < height; row++) {
            int dst = (dstY + row) * destinationStride + dstX * 4;
            int src = row * sourceStride;
            for (int i = 0; i < sourceStride; i++) {
                if (existing.data.get(dst + i) != patch.get(src + i)) {
                    identical = false;
                    break;
                }
            }
        }
        if (identical) {
            this.identicalPatches.incrementAndGet();
            return;
        }
        for (int row = 0; row < height; row++) {
            // Absolute and whole-row, so the record's own position never moves under a concurrent reader.
            existing.data.put((dstY + row) * destinationStride + dstX * 4, patch, row * sourceStride,
                    sourceStride);
        }
        // The surface now holds game bytes even when it started as an undefined allocation: the level is what GL has
        // (undefined elsewhere, this patch here), so a mirror may be built from it. Set after the copy, so a reader
        // that sees the flag also sees the rows. The serial bumps at the same point for the same reason: a consumer
        // that sees the new number is guaranteed the rows it belongs to are visible.
        existing.serial = this.texUploadSerial.incrementAndGet();
        existing.gamePixels = true;
        this.subCaptures.incrementAndGet();
    }

    /**
     * {@code glCompressedTexImage2D}: the extent is recorded like any other upload, but the payload is DXT/S3TC or
     * similar and decoding it is not this stub's job - the upload is counted as unconverted, which leaves whatever the
     * level held before it.
     */
    private void compressedTexImage(long[] c) {
        int target = (int) c[0];
        recordExtent(target, (int) c[1], (int) c[2], (int) c[3], (int) c[4]);
        if (!isProxyTarget(target) && c[7] != 0L) {
            this.unconvertedUploads.incrementAndGet();
        }
    }

    /**
     * Converts one upload's pixels to RGBA8, top row first, or {@code null} when the format/type pair or the recorded
     * unpack state is not one this understands.
     * <p>
     * A {@code null} is never turned into a guess: the caller counts it and keeps the level's previous content, because
     * an upload read with the wrong layout would be a silently wrong atlas rather than a missing one. Understood are
     * {@code GL_RGBA} and {@code GL_BGRA} with {@code GL_UNSIGNED_BYTE}, either of those with
     * {@code GL_UNSIGNED_INT_8_8_8_8_REV} - the packed {@code 0xAARRGGBB} form the atlas uploads use - or with
     * {@code GL_UNSIGNED_INT_8_8_8_8}, and
     * {@code GL_RGB} with {@code GL_UNSIGNED_BYTE}. Rows are read where GL would read them, padded to the recorded
     * unpack alignment; a row length or a pixel/row skip that is not the default is refused rather than misread.
     */
    private ByteBuffer uploadToRgba8(int width, int height, int format, int type, long pixels) {
        if (this.pixelUnpackBuffer > 0) {
            // With an unpack buffer bound, pixels is an offset into it. The buffer's storage is this stub's own block,
            // so the offset is resolved to an address inside it - bounds-checked against the rows GL would read.
            ByteBuffer storage = this.bufferStore.get(this.pixelUnpackBuffer);
            long needed = (long) width * (long) height * 4L;
            if (storage != null && pixels >= 0L && width > 0 && height > 0
                    && pixels + needed <= storage.capacity()) {
                pixels = memAddress(storage) + pixels;
                int bound = this.pixelUnpackBuffer;
                this.pixelUnpackBuffer = 0;
                try {
                    return uploadToRgba8(width, height, format, type, pixels);
                } finally {
                    this.pixelUnpackBuffer = bound;
                }
            }
        }
        String pboRefusal = pixelBufferRefusal(this.pixelUnpackBuffer, pixels, "texture upload");
        if (pboRefusal != null) {
            if (this.pixelBufferRefusals < 8) {
                this.pixelBufferRefusals++;
                System.out.println("[Celtium] CELTIUM_PBO_REFUSED " + pboRefusal);
            }
            return null;
        }
        if (pixels == 0L || width <= 0 || height <= 0) {
            return null;
        }
        if ((this.unpackRowLength != 0 && this.unpackRowLength != width)
                || this.unpackSkipPixels != 0 || this.unpackSkipRows != 0) {
            return null;
        }
        long bytes = (long) width * (long) height * 4L;
        if (bytes > MAX_CAPTURE_BYTES) {
            return null;
        }
        boolean bgra = format == GL_BGRA;
        if (type == GL_UNSIGNED_BYTE && (format == GL_RGBA || bgra)) {
            int rowBytes = width * 4;
            int stride = aligned(rowBytes, this.unpackAlignment);
            ByteBuffer source = MemoryUtil.memByteBuffer(pixels, (int) ((long) (height - 1) * stride + rowBytes));
            ByteBuffer out = ByteBuffer.allocateDirect((int) bytes);
            long outAddress = memAddress(out);
            for (int row = 0; row < height; row++) {
                int base = row * stride;
                if (!bgra) {
                    // Absolute on both sides: the row's source offset and the record's offset move together, and no
                    // buffer position has to be legal at any intermediate point.
                    out.put(row * rowBytes, source, base, rowBytes);
                } else {
                    // One word per texel: four checked get/put pairs per pixel were the top of the client thread while
                    // a world was entered (JFR leg entry-jfr2: Buffer.nextPutIndex under uploadToRgba8).
                    long from = pixels + base;
                    long to = outAddress + (long) row * rowBytes;
                    for (int i = 0; i < rowBytes; i += 4) {
                        int bgraWord = MemoryUtil.memGetInt(from + i);
                        MemoryUtil.memPutInt(to + i, swapRedBlue(bgraWord));
                    }
                }
            }
            out.clear();
            return out;
        }
        if (type == GL_UNSIGNED_BYTE && format == GL_RGB) {
            // Three bytes per pixel, expanded with an opaque alpha; the source rows carry GL's alignment padding.
            int rowBytes = width * 3;
            int stride = aligned(rowBytes, this.unpackAlignment);
            ByteBuffer source = MemoryUtil.memByteBuffer(pixels, (int) ((long) (height - 1) * stride + rowBytes));
            ByteBuffer out = ByteBuffer.allocateDirect((int) bytes);
            for (int row = 0; row < height; row++) {
                int base = row * stride;
                for (int x = 0; x < width; x++) {
                    int pixel = base + x * 3;
                    out.put(source.get(pixel)).put(source.get(pixel + 1))
                            .put(source.get(pixel + 2)).put((byte) 0xFF);
                }
            }
            out.clear();
            return out;
        }
        boolean reversed = type == GL_UNSIGNED_INT_8_8_8_8_REV;
        if ((reversed || type == GL_UNSIGNED_INT_8_8_8_8) && (format == GL_RGBA || bgra)) {
            // The packed forms read the 32-bit word in host order. _REV puts the first component in the least
            // significant bits - for GL_BGRA that is the game's own 0xAARRGGBB int - and the plain form puts it in the
            // most significant bits (Xaero's Minimap uploads its tiles as GL_BGRA/GL_UNSIGNED_INT_8_8_8_8).
            int stride = aligned(width * 4, this.unpackAlignment);
            ByteBuffer source = MemoryUtil.memByteBuffer(pixels, (height - 1) * stride + width * 4)
                    .order(java.nio.ByteOrder.nativeOrder());
            ByteBuffer out = ByteBuffer.allocateDirect((int) bytes);
            long outAddress = memAddress(out);
            boolean nativeLittle = java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;
            for (int row = 0; row < height; row++) {
                long from = pixels + (long) row * stride;
                long to = outAddress + (long) row * width * 4L;
                for (int x = 0; x < width; x++) {
                    int packed = MemoryUtil.memGetInt(from + x * 4L);
                    if (!reversed) {
                        packed = Integer.reverseBytes(packed);
                    }
                    // packed holds the first component in its low byte; the record is R,G,B,A in memory order, which
                    // on a little-endian host is the word with R in the low byte.
                    int rgba = bgra ? swapRedBlue(packed) : packed;
                    MemoryUtil.memPutInt(to + x * 4L, nativeLittle ? rgba : Integer.reverseBytes(rgba));
                }
            }
            out.clear();
            return out;
        }
        return null;
    }

    /** Swaps the low and third bytes of a texel word: B,G,R,A to R,G,B,A in memory order on a little-endian host. */
    private static int swapRedBlue(int word) {
        return ((word >>> 16) & 0xFF) | (word & 0xFF00FF00) | ((word & 0xFF) << 16);
    }

    /** {@code bytes} rounded up to a GL unpack alignment, which is always a power of two: 1, 2, 4 or 8. */
    private static int aligned(int bytes, int alignment) {
        return (bytes + alignment - 1) & ~(alignment - 1);
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
            stub.texLevels.put(texLevelKey(name, level), new int[] { levelWidth, levelHeight, GL_RGBA });
            stub.undefinedLevel(name, level, levelWidth, levelHeight, 1);
        }
    }

    /** Deletes the named storage record too when native glDeleteTextures cannot reach callback dispatch. */
    public static void forgetTextureAllocation(int name) {
        CeltiumGlStub stub = installed;
        if (stub != null && name > 0) {
            stub.deleteTexture(name);
        }
    }

    /**
     * A named texture-level query against the GL-less record, without crossing an LWJGL GL45C
     * native entry point. The value is exactly what our DSA stub function would answer;
     * a texture with no level record returns 0 instead of inventing dimensions.
     * Call this only when {@link #isInstalled()} is true.
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
            case 0x803C: // GL_TEXTURE_RED_SIZE and friends report a plausible bit depth
            case 0x803D:
            case 0x803E:
            case 0x803F:
            case 0x8040:
            case 0x8041:
                return 8;
            default:
                return 0;
        }
    }

    // ---- buffer storage: the minimum that keeps upload/mapping code paths alive ------------------------

    /**
     * The buffer whose storage an operation applies to: the bound name for {@code glBufferData}, or the name arg
     * itself for {@code glNamedBufferData}.
     */
    private int boundBuffer(int target) {
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
                : target >= 0 && target < this.bufferBindings.length ? this.bufferBindings[target] : 0;
    }

    private int bufferFor(Fn fn, long[] c) {
        return fn.namedObject ? (int) c[0] : boundBuffer((int) c[0]);
    }

    private void bufferData(Fn fn, long[] c) {
        bufferDataFor(bufferFor(fn, c), c[1], c[2]);
    }

    /** The one buffer-data producer: the dispatch case and the transformer's route both land here. */
    private void bufferDataFor(int name, long size, long data) {
        if (name == 0 || size < 0 || size > Integer.MAX_VALUE) {
            return;
        }
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
        if (nativeBuffer != null) {
            nativeBuffer.replaceStorage(data == 0L ? null : MemoryUtil.memByteBuffer(data, (int) size), (int) size);
            return;
        }
        if (size == 0) {
            // glBufferData(size=0) is a legal redefinition that leaves the buffer empty; the old code returned early and
            // kept the previous storage and size, so the buffer still mapped and still answered the old extent (#387).
            this.bufferStore.remove(Integer.valueOf(name));
            this.bufferLogicalSize.put(Integer.valueOf(name), Integer.valueOf(0));
            return;
        }
        this.bufferLogicalSize.put(Integer.valueOf(name), Integer.valueOf((int) size));
        // The storage is reused whenever it is already large enough, rather than replaced. The game's staging buffers
        // are mapped persistently - the mirror this renderer reads its meshes from is one - and a new allocation moves
        // the address the caller is still writing to, which shows up as geometry built from whatever the old storage
        // happened to hold. Keeping the block makes the address a buffer name returns stable for the name's lifetime,
        // which is the property a persistent mapping needs; a larger request still has to move it.
        ByteBuffer storage = this.bufferStore.get(name);
        if (storage == null || storage.capacity() < size) {
            storage = ByteBuffer.allocateDirect((int) size);
            this.bufferStore.put(name, storage);
        }
        if (data != 0L) {
            ByteBuffer source = MemoryUtil.memByteBuffer(data, (int) size);
            int position = storage.position();
            storage.clear();
            storage.put(source);
            storage.clear();
            storage.position(position);
        } else {
            storage.clear();
        }
    }

    /**
     * {@return the byte offset a {@code glMapBufferRange} may map}, or -1 when the range is not within the buffer.
     * <p>
     * GL's contract: {@code offset} and {@code length} are non-negative, {@code length > 0}, and the range must lie
     * inside the buffer. Anything else is an error the caller has to see, not a range quietly moved to zero (audit #361:
     * offset -1 was answered as 0, and a length running past the end was not looked at).
     */
    /**
     * {@return the size GL last gave this buffer, falling back to the storage's capacity when nothing was recorded}.
     *
     * @param name     the buffer name
     * @param capacity the backing storage's size
     */
    private int logicalSizeOf(int name, int capacity) {
        Integer recorded = this.bufferLogicalSize.get(Integer.valueOf(name));
        return recorded == null ? capacity : recorded.intValue();
    }

    static long mapRangeOffset(long offset, long length, long capacity) {
        if (offset < 0L || length <= 0L || capacity < 0L) {
            return -1L;
        }
        // Written this way round so the sum cannot overflow: length is bounded by the capacity instead.
        if (length > capacity || offset > capacity - length) {
            return -1L;
        }
        return offset;
    }

    /** Calls and refusals of {@link #mapBuffer}, so an invalid range is a counted event rather than a silent clamp. */
    private long mapBufferCalls;
    private long mapBufferRefusals;

    /** {@return the map calls and how many were refused for an out-of-range request}, for the verification line. */
    public static String mapBufferCensus() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "calls=none refusals=none";
        }
        return "calls=" + stub.mapBufferCalls + " refusals=" + stub.mapBufferRefusals;
    }

    private long mapBuffer(Fn fn, long[] c) {
        boolean range = fn.args.length == 4;
        return mapBufferFor(bufferFor(fn, c), range, range ? c[1] : 0L, range ? c[2] : 0L, range ? c[3] : 0L);
    }

    /**
     * {@code glMapBuffer}/{@code glMapBufferRange} from the native transformer's route: LWJGL's {@code nglMapBuffer}
     * used to fall through to the default zero, which LWJGL hands the caller as a null buffer - Xaero's Minimap then
     * crashed on its first tile upload ({@code PixelBuffers.glMapBuffer(...) is null}).
     */
    public static long celtium$mapBuffer(int target, boolean range, long offset, long length, int access) {
        CeltiumGlStub stub = installed;
        return stub == null ? 0L : stub.mapBufferFor(stub.boundBuffer(target), range, offset, length, access);
    }

    /** {@code glUnmapBuffer}: the storage is the caller's memory already, so there is nothing to flush. */
    public static boolean celtium$unmapBuffer(int target) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return true;
        }
        NativeGeometryBuffer mapped = NativeGeometryBuffer.find(stub.boundBuffer(target));
        return mapped == null || mapped.unmap();
    }

    private long mapBufferFor(int name, boolean range, long rangeOffset, long rangeLength, long access) {
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
        if (nativeBuffer != null) {
            this.mapBufferCalls++;
            // VertexBuffer API storage is transiently mappable, not persistent/coherent mapped storage.
            long address = range && (access & 0xC0L) != 0L ? 0L
                    : nativeBuffer.map(range ? rangeOffset : 0, range ? rangeLength : nativeBuffer.byteSize());
            if (address == 0L) this.mapBufferRefusals++;
            return address;
        }
        ByteBuffer storage = this.bufferStore.get(name);
        if (storage == null) {
            // Mapping a buffer with no allocation: GL would fault - an empty block keeps the caller alive while
            // making writes visibly land nowhere.
            storage = ByteBuffer.allocateDirect(0);
        }
        long base = memAddress(storage);
        if (range) {
            // glMapBufferRange(target, offset, length, access): the range is validated, not corrected. Returning base +
            // a clamped offset turned an invalid request into a valid-looking mapping (audit #361 measured offset = -1
            // answered as 0, and a length past the end simply ignored), which lets a caller write outside the buffer it
            // thinks it mapped. GL returns NULL for a range that is not within the buffer, and so does this.
            long offset = mapRangeOffset(rangeOffset, rangeLength, this.logicalSizeOf(name, storage.capacity()));
            this.mapBufferCalls++;
            if (offset < 0L) {
                this.mapBufferRefusals++;
                if (this.mapBufferRefusals <= 3) {
                    System.out.println("[Celtium] CELTIUM_MAP_RANGE_REFUSED buffer=" + name + " offset=" + rangeOffset
                            + " length=" + rangeLength + " capacity=" + storage.capacity());
                }
                return 0L;
            }
            return base + offset;
        }
        this.mapBufferCalls++;
        return base;
    }

    // ---- client-array drawing: the one GL drawing entry point that leaves this class for the frame ---------------

    // ---- texture environment, per texture unit -------------------------------------------------------------------
    //
    // GL keeps one environment per unit; vanilla's hurt flash switches to the lightmap unit and sets an INTERPOLATE
    // combine with a constant colour there, then asks it back when it builds the immediate batch's combine state. The
    // stub records what the game set instead of answering from GL, which under GL-less has nothing to answer from.
    private static final int GL_TEXTURE_ENV = 0x2300;
    private static final int GL_TEXTURE_ENV_MODE = 0x2200;
    private static final int GL_COMBINE_RGB = 0x8571;
    private static final int GL_INTERPOLATE = 0x8575;
    private static final int GL_COMBINE_ALPHA = 0x8572;
    private static final int GL_TEXTURE_ENV_COLOR = 0x2201;
    private static final int GL_MODULATE = 0x2100;
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
    // GL keeps the filter and wrap state on the texture object, and the game sets it through
    // {@code GlStateManager.glTexParameteri -> GL11.glTexParameteri}: TextureUtil.setTextureClamped/setTextureBlurMipmap
    // for every uploaded texture, AbstractTexture.setBlurMipmapDirect, Framebuffer's attachment filters and
    // RenderGlobal's sky texture. The stub used to no-op every setter and answer every query with one hardcoded pair,
    // so the mirror's sampler was built CLAMP_TO_EDGE for textures the game had asked to REPEAT. Vanilla depends on
    // the difference: GuiOptions.drawBackground and Cleanroom's patched GuiSlot.drawContainerBackground draw
    // textures/gui/options_background.png with UVs up to height/32, which tiles only under REPEAT - and that texture
    // is given exactly that by TextureUtil.uploadTextureImageAllocate(..., clamp=false) ->
    // setTextureClamped(false) -> glTexParameteri(GL_TEXTURE_WRAP_S/T, 0x2901).
    //
    // The record is keyed by texture *name* and pname rather than by binding, because the reads that need it name the
    // texture: the mirror's wrap/filter query is the DSA glGetTextureParameter*, which carries the name, and it may
    // run on a different unit than the one that set the parameter. Deleted names are never reissued by this stub, so
    // a record left behind by glDeleteTextures cannot be read as a later texture's state.
    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    /** GL_TEXTURE_BORDER_COLOR is a four-component vector; a scalar record cannot hold it, so it is not recorded. */
    private static final int GL_TEXTURE_BORDER_COLOR = 0x1004;
    /** GL's initial state for a fresh texture object: the answer when the game never set the pname. */
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_NEAREST_MIPMAP_LINEAR = 0x2702;
    private static final int GL_REPEAT = 0x2901;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_NEAREST = 0x2600;

    /**
     * Per-texture counter of recorded parameter changes, so a consumer can skip re-reading four parameters it already
     * read for this texture.
     * <p>
     * {@code VulkanGlTexture.confirmSamplerStateFromRecord} re-consults the wrap and filter records on every texture
     * lookup - four to six boxed map lookups per draw - because BH-23 requires a parameter changed after the first
     * draw to be seen. A version bumped here, read there, keeps that requirement (a change still re-confirms) while a
     * texture whose parameters nobody touched costs one array read.
     */
    private int[] texParamVersions = new int[1024];

    /** Bumps the recorded-parameter version of this texture name, growing the table as names appear. */
    private void celtium$bumpTextureParameterVersion(int textureName) {
        if (textureName <= 0) {
            return;
        }
        if (textureName >= this.texParamVersions.length) {
            int grown = Math.max(textureName + 1, this.texParamVersions.length * 2);
            this.texParamVersions = java.util.Arrays.copyOf(this.texParamVersions, grown);
        }
        this.texParamVersions[textureName]++;
    }

    /** {@return how many times this texture's recorded parameters changed}, 0 when the game never set one. */
    public static int textureParameterVersion(int textureName) {
        CeltiumGlStub stub = installed;
        if (stub == null || textureName <= 0 || textureName >= stub.texParamVersions.length) {
            return 0;
        }
        return stub.texParamVersions[textureName];
    }

    /** Recorded values, keyed {@code (textureName << 32) | pname}. Bounded: past the limit the call is counted. */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<Integer> texParams =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger texParamCalls =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger texParamUnbound =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger texParamOverflow =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger texParamQueries =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final int TEX_PARAM_RECORD_LIMIT = 1 << 16;
    /** The distinct (name,pname,value) triples the {@code CELTIUM_TEX_PARAM} instrument has named. Bounded. */
    private static final java.util.Set<String> TEX_PARAM_SHAPES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> TEX_PARAM_QUERY_SHAPES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static long texParamKey(int textureName, int pname) {
        return ((long) textureName << 32) | (pname & 0xFFFFFFFFL);
    }

    /**
     * The call site for a parameter set, or the empty string once the instrument has named its shapes.
     * <p>
     * Reading the stack is the expensive part of this instrument, and a texture load sets four parameters per texture,
     * so it is taken only while the bounded shape set is still being filled.
     */
    private static String celtium$paramSite() {
        return TEX_PARAM_SHAPES.size() < 24 ? celtium$callSite(5) : "";
    }

    /** The first element of an array-valued texture parameter: every scalar pname this record carries is one wide. */
    private static int celtium$firstParamValue(Fn fn, long values) {
        if (values == 0L) {
            return 0;
        }
        return fn.name.endsWith("fv") ? Math.round(MemoryUtil.memGetFloat(values)) : MemoryUtil.memGetInt(values);
    }

    private static String texParamPname(int pname) {
        switch (pname) {
            case GL_TEXTURE_MAG_FILTER: return "MAG_FILTER";
            case GL_TEXTURE_MIN_FILTER: return "MIN_FILTER";
            case GL_TEXTURE_WRAP_S: return "WRAP_S";
            case GL_TEXTURE_WRAP_T: return "WRAP_T";
            default: return "pname";
        }
    }

    private static String texParamValue(int value) {
        switch (value) {
            case GL_REPEAT: return "REPEAT";
            case GL_CLAMP_TO_EDGE: return "CLAMP_TO_EDGE";
            case GL_LINEAR: return "LINEAR";
            case GL_NEAREST: return "NEAREST";
            case GL_NEAREST_MIPMAP_LINEAR: return "NEAREST_MIPMAP_LINEAR";
            case 0x2602: return "NEAREST_MIPMAP_NEAREST";
            case 0x2703: return "LINEAR_MIPMAP_LINEAR";
            case 0x2701: return "LINEAR_MIPMAP_NEAREST";
            default: return "0x" + Integer.toHexString(value);
        }
    }

    /**
     * Records one texture parameter. {@code textureName} is the texture the call applies to - the bound one for the
     * non-DSA forms, the explicit first argument for the DSA ones - and a call with no texture bound (which real GL
     * rejects with GL_INVALID_OPERATION) is counted rather than filed under a name the game never used.
     */
    private void celtium$setTextureParameter(int textureName, int pname, int value, String site) {
        if (textureName <= 0) {
            this.texParamUnbound.incrementAndGet();
            return;
        }
        if (pname == GL_TEXTURE_BORDER_COLOR) {
            return;
        }
        long key = texParamKey(textureName, pname);
        if (!this.texParams.containsKey(key)
                && this.texParams.size() >= TEX_PARAM_RECORD_LIMIT) {
            this.texParamOverflow.incrementAndGet();
            return;
        }
        this.texParams.put(key, Integer.valueOf(value));
        celtium$bumpTextureParameterVersion((int) (key >>> 32));
        int calls = this.texParamCalls.incrementAndGet();
        if (org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(TEX_PARAM_SHAPES.size(), 24)) {
            String shape = textureName + "/" + pname + "=" + value;
            if (TEX_PARAM_SHAPES.add(shape)) {
                System.out.println("[Celtium] CELTIUM_TEX_PARAM texture=" + textureName
                        + " pname=0x" + Integer.toHexString(pname) + " (" + texParamPname(pname) + ")"
                        + " value=0x" + Integer.toHexString(value) + " (" + texParamValue(value) + ")"
                        + " calls=" + calls + " distinct=" + TEX_PARAM_SHAPES.size()
                        + " site=[" + site + "]");
            }
        }
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
        celtium$setTextureParameter(bound < 0 ? 0 : bound, pname, value, site);
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
            long at = address + (long) i * this.vertexPointerStride + 12L;
            float u = MemoryUtil.memGetFloat(at);
            float v = MemoryUtil.memGetFloat(at + 4L);
            uvs.append(i == 0 ? "" : " ").append('(').append(celtium$round3(u)).append(',').append(celtium$round3(v))
                    .append(") cell=").append(Math.round(u * 16.0f)).append(',').append(Math.round(v * 16.0f));
        }
        System.out.println("[Celtium] CELTIUM_PARTICLE_UV count=" + count + " stride=" + this.vertexPointerStride
                + " uvs=[" + uvs + "]");
    }


    /**
     * {@return the value a texture parameter query answers}: the record when the game set this pname on this texture,
     * otherwise GL's own initial state for the four pnames a fresh texture has, and 0 for everything else.
     * <p>
     * The default is the honest stub answer rather than a safe one: a texture the game never parameterised really does
     * wrap by repeating in GL, which is why the options background tiles without anyone setting a wrap on it.
     * <p>
     * {@code -Dceltium.stub.texWrapClampControl=true} is the one-flag control for the settings-background defect: it
     * answers exactly what this class hardcoded before the record existed (wrap CLAMP_TO_EDGE, filters LINEAR), so the
     * same jar, scene and pose can be run one flag apart and the tiling compared. A control run says so in its own
     * census line.
     */
    private static final boolean TEX_WRAP_CLAMP_CONTROL =
            Boolean.getBoolean("celtium.stub.texWrapClampControl");

    private static int textureParameterValue(int textureName, int pname) {
        if (TEX_WRAP_CLAMP_CONTROL) {
            if (pname == GL_TEXTURE_WRAP_S || pname == GL_TEXTURE_WRAP_T) {
                return GL_CLAMP_TO_EDGE;
            }
            if (pname == GL_TEXTURE_MAG_FILTER || pname == GL_TEXTURE_MIN_FILTER) {
                return GL_LINEAR;
            }
            return 0;
        }
        CeltiumGlStub stub = installed;
        if (stub != null && textureName > 0) {
            Integer recorded = stub.texParams.get(texParamKey(textureName, pname));
            if (recorded != null) {
                return recorded.intValue();
            }
        }
        switch (pname) {
            case GL_TEXTURE_WRAP_S:
            case GL_TEXTURE_WRAP_T:
                return GL_REPEAT;
            case GL_TEXTURE_MAG_FILTER:
                return GL_LINEAR;
            case GL_TEXTURE_MIN_FILTER:
                return GL_NEAREST_MIPMAP_LINEAR;
            default:
                return 0;
        }
    }

    /**
     * {@return the filter or wrap value recorded for this texture and pname, or -1 when the game never set it}.
     * <p>
     * This is the mirror's door to the record: {@link #textureParameter} answers GL's default for an unset pname,
     * while a caller that wants to keep its own fallback (the mirror's clamp) can tell the two apart here.
     */
    public static int recordedTextureParameter(int textureName, int pname) {
        CeltiumGlStub stub = installed;
        if (stub == null || textureName <= 0) {
            return -1;
        }
        Integer recorded = stub.texParams.get(texParamKey(textureName, pname));
        return recorded == null ? -1 : recorded.intValue();
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
        return recordedTextureParameter(textureName, pname) != -1;
    }

    /** The named texture parameters, bounded: the record census and the query answers. */
    private static String texParamSummary() {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return "texture params: recorded=0 textures=0 repeat=0 clamp=0 unbound=0 overflow=0 queries=0";
        }
        int repeat = 0;
        int clamp = 0;
        for (Map.Entry<Long, Integer> entry : stub.texParams.entrySet()) {
            int pname = (int) (entry.getKey().longValue() & 0xFFFFFFFFL);
            if (pname != GL_TEXTURE_WRAP_S && pname != GL_TEXTURE_WRAP_T) {
                continue;
            }
            if (entry.getValue().intValue() == GL_REPEAT) {
                repeat++;
            } else if (entry.getValue().intValue() == GL_CLAMP_TO_EDGE) {
                clamp++;
            }
        }
        java.util.Set<Integer> textures = new java.util.HashSet<>();
        for (Long key : stub.texParams.keySet()) {
            textures.add(Integer.valueOf((int) (key.longValue() >>> 32)));
        }
        return "texture params: recorded=" + stub.texParams.size() + " textures=" + textures.size()
                + " wrapRepeat=" + repeat + " wrapClamp=" + clamp
                + " calls=" + stub.texParamCalls.get() + " unbound=" + stub.texParamUnbound.get()
                + " overflow=" + stub.texParamOverflow.get() + " queries=" + stub.texParamQueries.get()
                + " wrapClampControl=" + TEX_WRAP_CLAMP_CONTROL;
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
            List<Map.Entry<Integer, Long>> pnames = new ArrayList<>(stub.getIntegervPnames.entrySet());
            pnames.sort((a, b) -> Long.compare(b.getValue().longValue(), a.getValue().longValue()));
            text.append(" | getIntegervByPname:");
            int pnameShown = 0;
            for (Map.Entry<Integer, Long> entry : pnames) {
                if (pnameShown++ >= 12) {
                    break;
                }
                text.append(' ').append(Integer.toHexString(entry.getKey().intValue()))
                        .append('=').append(entry.getValue().longValue());
            }
        }
        return text.toString();
    }

    /** One query answer, named once per distinct shape so a run's log says what the mirror actually asked for. */
    private void celtium$noteTextureParameterQuery(String name, int textureName, int pname, int value, boolean recorded) {
        this.texParamQueries.incrementAndGet();
        if (!org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(TEX_PARAM_QUERY_SHAPES.size(), 12)) {
            return;
        }
        String shape = name + "/" + textureName + "/" + pname + "=" + value + "/" + recorded;
        if (TEX_PARAM_QUERY_SHAPES.add(shape)) {
            System.out.println("[Celtium] CELTIUM_TEX_PARAM_QUERY " + name
                    + " texture=" + textureName
                    + " pname=0x" + Integer.toHexString(pname) + " (" + texParamPname(pname) + ")"
                    + " value=0x" + Integer.toHexString(value) + " (" + texParamValue(value) + ")"
                    + " recorded=" + recorded
                    + " source=" + (recorded ? "stub-record" : "gl-default")
                    + " site=[" + celtium$callSite(6) + "]");
        }
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

    /** The transformer's entry for {@code glReadPixels}: forwards to the installed stub when one exists. */
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
            // and refuse an offset outside it rather than write through a small integer taken as an address.
            ByteBuffer storage = stub.bufferStore.get(stub.pixelPackBuffer);
            if (storage == null || address < 0L || address >= storage.capacity()) {
                return;
            }
            capacity = storage.capacity() - address;
            address = memAddress(storage) + address;
        }
        Object backend = org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {
            vulkan.celtium$getTexImage(stub.textures.texture2d(), level, format, type, address, capacity);
        }
    }

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
    private static final int GL_TEXTURE_2D = 0x0DE1;
    /**
     * GL_FOG: the enable bit {@code RenderGlobal.renderSky} sets around the sky dome (RenderGlobal.java:1337-1345) and
     * around the horizon plane (:1478-1471) and clears before the stars (:1354). It is read at draw time, because the
     * immediate path's fog is per batch, not per frame.
     */
    private static final int GL_FOG = 0x0B60;
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
    private static final java.util.concurrent.atomic.AtomicBoolean DRAWARRAY_FIRST_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();

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
    private void drawArrays(int mode, int first, int count) { drawArrays(mode, first, count, false); }

    private void drawArrays(int mode, int first, int count, boolean compile) {
        DRAWARRAY_CALLS.incrementAndGet();
        // The owned-buffer publication is consumed by every draw, matched or not: one publication belongs to the one
        // glDrawArrays that follows it, and a publication left behind would otherwise be read as the next draw's
        // ownership claim - which is the mistake this entry point exists to remove.
        long[] owned = celtium$consumeOwnedClientBuffer();
        if (count <= 0) {
            // GL draws nothing for an empty range, and so does this: counting it as a gap would be a lie.
            DRAWARRAY_EMPTY.incrementAndGet();
            celtium$reportDrawArrays();
            return;
        }
        // A mod can bind through Minecraft's VertexBuffer API and issue the draw through raw GL. Only that raw call
        // enters this decoder; the native API draw uses the retained mesh directly and never mirrors a GL buffer.
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.bound();
        int boundName = NativeGeometryBuffer.boundId();
        int buffer = boundName;
        ByteBuffer storage = nativeBuffer != null ? nativeBuffer.compatibilityView()
                : buffer == 0 ? null : this.bufferStore.get(buffer);
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
        long offset = this.vertexPointerOffset;
        long end = 0L;
        long asked = 0L;
        int formatId = 0;
        String reason = null;
        if (storage == null && !clientMemory) {
            reason = "the bound array buffer " + buffer + " has no mirrored storage";
        } else if (!(this.enables[GL_VERTEX_ARRAY] != 0)) {
            reason = "GL_VERTEX_ARRAY is not enabled, so no vertex pointer describes this draw";
        } else if (mode != GL_QUADS && mode != 4 && mode != 6) {
            reason = "draw mode " + mode + " is not handled (quads/triangles/fans only)";
        } else if (mode == 6 && count < 3) {
            // A fan with fewer than three vertices has no triangles to present.
            DRAWARRAY_EMPTY.incrementAndGet();
            celtium$reportDrawArrays();
            return;
        } else if (this.vertexPointerSize != 3 || this.vertexPointerType != GL_FLOAT) {
            reason = "vertex pointer size=" + this.vertexPointerSize + " type=0x"
                    + Integer.toHexString(this.vertexPointerType)
                    + " is not the float3 position every layout this path decodes uses";
        } else {
            String[] layoutRefusal = new String[1];
            long storageBase = (clientMemory || storage == null) ? 0L : memAddress(storage, 0);
            int storageCapacity = storage == null ? 0 : storage.capacity();
            formatId = celtium$drawArrayFormat(this.vertexPointerStride, celtium$otherArrayMask(), count, first,
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
            asked = (long) first * this.vertexPointerStride + (long) count * this.vertexPointerStride;
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
                end = offset + asked;
                if (end > storage.capacity()) {
                    reason = "the draw reads to byte " + end + " of a " + storage.capacity() + "-byte buffer";
                }
            }
        }
        if (reason == null) {
            celtium$noteClientTuple(clientMemory, buffer, count, formatId);
        }
        long address = 0L;
        if (reason == null) {
            int stride = this.vertexPointerStride;
            address = (clientMemory ? offset : memAddress(storage, 0) + offset) + (long) first * stride;
            int decodedStride = this.vertexPointerStride == 48 && formatId == FORMAT_POSITION_TEX_COLOR ? 24
                    : this.vertexPointerStride == 32 && formatId == 9 ? 24
                    : this.vertexPointerStride == 32 && formatId == 8 ? 28 : this.vertexPointerStride;
            if (compile) {
                long bytes = (long) count * decodedStride;
                if (bytes > MAX_LIST_PAYLOAD_BYTES) {
                    this.listPayloadsUnsized++;
                    DRAWARRAY_REFUSED.incrementAndGet();
                    return;
                }
                ByteBuffer snapshot = celtium$copyClientVerticesForDraw(address, count, this.vertexPointerStride, formatId);
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
                if (hasItemTwin(this.vertexPointerStride, formatId)) {
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
                    boolean asItem = hasItemTwin(this.vertexPointerStride, formatId) && lightingOn(backend);
                    ByteBuffer packed = asItem ? copyClientVerticesAsItem(address, count, formatId)
                            : decodedStride == this.vertexPointerStride ? null
                            : celtium$copyClientVerticesForDraw(address, count, this.vertexPointerStride, formatId);
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
                    DRAWARRAY_DRAWN.incrementAndGet();
                    DRAWARRAY_VERTICES.addAndGet(count);
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
                            long at = address + (long) i * this.vertexPointerStride;
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
                            long at = address + (long) i * this.vertexPointerStride;
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
                        int colourOffset = this.vertexPointerStride >= 24 ? 20 : 16;
                        for (int i = 0; i < 4 && i < count; i++) {
                            long at = address + (long) i * this.vertexPointerStride + colourOffset;
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
                            long at = address + (long) (count - 1) * this.vertexPointerStride;
                            tailUv.setLength(0);
                            tailUv.append('(').append(celtium$round3(MemoryUtil.memGetFloat(at + 12))).append(',')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at + 16))).append(')');
                        }
                        System.out.println("[Celtium] CELTIUM_DRAWARRAY_LAYOUT first=" + first + " count=" + count
                                + " uvLast=" + tailUv
                                + " stride=" + this.vertexPointerStride + " format=" + formatId + " mode=" + mode
                                + " col0..col3=[" + colours + "]"
                                + " uv0..uv3=[" + uvs + "]"
                                + " buffer=" + buffer + " clientMemory=" + clientMemory + " pointerOffset=" + offset
                                + " pointerSize=" + this.vertexPointerSize + " pointerType=0x"
                                + Integer.toHexString(this.vertexPointerType) + " pointerStride="
                                + this.vertexPointerStride + " otherArrays=" + celtium$otherArrayNames()
                                + " v0..v3=[" + vertices + "] "
                                + celtium$mirroredMatrices(backend)
                                + " texture2dEnabled=" + (this.enables[GL_TEXTURE_2D] != 0)
                                + " site=[" + celtium$callSite(10) + "]");
                        drawArrayFirst = "mode=" + mode + " first=" + first + " count=" + count + " stride="
                                + this.vertexPointerStride + " format=" + formatId + " buffer=" + buffer
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
        celtium$noteDrawArrayRefusal(reason);
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
        String key = this.drawFbo + "/" + this.viewportW + "x" + this.viewportH + "/" + shortReason;
        if (!REFUSAL_CONTEXTS.add(key)) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_DRAWARRAY_REFUSAL_CONTEXT drawFbo=" + this.drawFbo
                + " readFbo=" + this.readFbo
                + " viewport=" + this.viewportW + "x" + this.viewportH
                + " stride=" + this.vertexPointerStride
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
            if ((this.enables[caps[i]] != 0)) {
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
        if ((this.enables[GL_NORMAL_ARRAY] != 0)) {
            mask |= 1;
        }
        if ((this.enables[GL_COLOR_ARRAY] != 0)) {
            mask |= 2;
        }
        if ((this.enables[GL_TEXTURE_COORD_ARRAY] != 0)) {
            mask |= 4;
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
        } else if (stride == 48 && otherArrays == 7
                && celtium$splitArraysValid(count, first, clientMemory, storageBase, storageCapacity, refusalOut)) {
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
     * {@return whether {@code [address, address + bytes)} lies inside one readable mapping of this process}.
     * <p>
     * This is the weak entry point, used only when the owned-buffer publication does not describe the draw. A client
     * pointer carries no length and no owner: it is a process address the game wrote, and dereferencing one that is
     * stale, freed or never valid faults the JVM, which is a worse outcome than the blank menu this task exists to
     * fix. So the mapping list the kernel already keeps is read once and cached, and a draw is only decoded when the
     * whole range it asks for is inside a single readable mapping. A miss refreshes the list once, because a buffer
     * allocated since the last read is a legitimate miss; a second miss for the same address is a refusal, and the
     * refresh rate is bounded so a run cannot turn a bad pointer into a parsing loop.
     * <p>
     * What this is <em>not</em>: an ownership or lifetime record. A hit says the range is readable at this moment, not
     * that it is still the game's buffer or that it will stay mapped; the cached list can also go stale between
     * refreshes, and the refresh budget (64) is a bound on this guard rather than a property of the memory. The
     * authoritative statement about a client-array draw is the {@link #publishClientArrayBuffer owned-buffer
     * publication}; this function exists for the calls that do not come through Minecraft's uploader.
     * <p>
     * Linux-only by construction ({@code /proc/self/maps}); where that file cannot be read this returns false, so a
     * pointer-only client-memory draw stays a named refusal rather than becoming an unguarded read - and on such a
     * system the owned-buffer path still decodes the game's own batches.
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
        ReadableRange[] ranges = this.readableRanges;
        if (ranges == null || celtium$inRange(ranges, address, end) < 0) {
            ranges = celtium$refreshReadableMappings();
            int hit = celtium$inRange(ranges, address, end);
            if (hit < 0) {
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

    /** Takes ownership at compile/submit time; no replay may read the caller's reused array. */
    /**
     * Validates the split arrays for a stride-48 draw and arms the repack with their per-draw bases.
     * <p>
     * Asked at the format decision, not at the copy: the first version validated later, and the draw that reached the
     * copy through a path without that validation copied from address zero and took the JVM down with SIGSEGV. The
     * acceptance of the layout and the validity of its pointers are the same decision.
     */
    private boolean celtium$splitArraysValid(int count, int first, boolean clientMemory, long storageBase,
            int storageCapacity, String[] refusalOut) {
        if (!celtium$texColorPointersKnown()) {
            return false;
        }
        int texStride = this.texCoordPointerStride > 0 ? this.texCoordPointerStride : 8;
        boolean floatColor = this.colorPointerType == GL_FLOAT;
        int colorStride = this.colorPointerStride > 0 ? this.colorPointerStride : (floatColor ? 16 : 4);
        long texBytes = (long) (count - 1) * texStride + 8L;
        long colorBytes = (long) (count - 1) * colorStride + (floatColor ? 16L : 4L);
        long texFrom;
        long colorFrom;
        if (clientMemory) {
            texFrom = this.texCoordPointerOffset + (long) first * texStride;
            colorFrom = this.colorPointerOffset + (long) first * colorStride;
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
            if (this.texCoordPointerOffset + texBytes > storageCapacity) {
                refusalOut[0] = "the split texture array reads to byte " + (this.texCoordPointerOffset + texBytes)
                        + " of a " + storageCapacity + "-byte buffer";
                return false;
            }
            if (this.colorPointerOffset + colorBytes > storageCapacity) {
                refusalOut[0] = "the split colour array reads to byte " + (this.colorPointerOffset + colorBytes)
                        + " of a " + storageCapacity + "-byte buffer";
                return false;
            }
            texFrom = storageBase + this.texCoordPointerOffset + (long) first * texStride;
            colorFrom = storageBase + this.colorPointerOffset + (long) first * colorStride;
        }
        this.texColorRepackTexBase = texFrom;
        this.texColorRepackColorBase = colorFrom;
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
        if (stride == 48 && format == FORMAT_POSITION_TEX_COLOR && this.texColorRepackArmed
                && celtium$texColorPointersKnown()) {
            return celtium$repackPaddedTexColor(address, count);
        }
        return copyClientVertices(address, count, stride, format);
    }

    /**
     * Builds POSITION_TEX_COLOR vertices (position, float2 texture, 4 unsigned-byte colour) from the split client arrays
     * of a stride-48 draw. The bases were advanced by {@code first} and their ranges validated by the caller.
     */
    private ByteBuffer celtium$repackPaddedTexColor(long vertexAddress, int count) {
        int texStride = this.texCoordPointerStride > 0 ? this.texCoordPointerStride : 8;
        boolean floatColor = this.colorPointerType == GL_FLOAT;
        int colorStride = this.colorPointerStride > 0 ? this.colorPointerStride : (floatColor ? 16 : 4);
        ByteBuffer out = MemoryUtil.memAlloc(Math.multiplyExact(count, 24));
        long to = MemoryUtil.memAddress(out);
        for (int i = 0; i < count; i++) {
            long target = to + (long) i * 24;
            MemoryUtil.memCopy(vertexAddress + (long) i * 48, target, 12);
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
            System.out.println("[Celtium] CELTIUM_CLIENT_ARRAY_REPACK layout=pos3f+tex2f+col4 fromStride=48 toStride=24"
                    + " vertices=" + count + " colorType=0x" + Integer.toHexString(this.colorPointerType)
                    + " texCoordType=0x" + Integer.toHexString(this.texCoordPointerType));
        }
        return out;
    }

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

    private void replayDisplayList(int listId) {
        // The replay, and the reason this field exists at all. Minecraft compiles a model once
        // (ModelRenderer.compileDisplayList, under GL_COMPILE) and draws it with glCallList(displayList);
        // VulkanModelQuadCapture's hook sits inside that compilation, so it saw the recording and refused the
        // quads with `reason=unknown-list list=0`, and the draw itself reached this no-op. Entities, the
        // player, held items, the sky, the stars and the horizon plane all arrive here.
        this.lastCalledList = listId;
        List<Object[]> list = this.displayLists.get(Integer.valueOf(this.lastCalledList));
        if (list == null) {
            // The spec: an undefined list is ignored, not an error. Counted so a silent miss is visible.
            this.displayListsUnresolved++;
        } else if (this.replayDepth < MAX_LIST_NESTING) {
            this.replayDepth++;
            this.executingList = this.lastCalledList;
            try {
                for (int i = 0; i < list.size(); i++) {
                    Object[] call = list.get(i);
                    Fn command = (Fn) call[0];
                    if (command.semantics == SEM_COMPILED_ARRAYS) replayCompiledArrays((long[]) call[1]);
                    else dispatchArgs(command, (long[]) call[1]);
                }
            } finally {
                this.replayDepth--;
                // Cleared on the way out so a quad drawn after the replay is not filed under the last list.
                this.executingList = this.replayDepth == 0 ? 0 : this.executingList;
            }
            this.displayListsReplayed++;
            if (this.lastCalledList >= 0 && this.lastCalledList < this.listReplayCounts.length) {
                this.listReplayCounts[this.lastCalledList]++;
                this.listReplayTotals[this.lastCalledList] += list.size();
            }
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
                DRAWARRAY_DRAWN.incrementAndGet();
                DRAWARRAY_VERTICES.addAndGet(c[3]);
            } else celtium$noteDrawArrayRefusal("compiled client arrays: "
                    + (backend == null ? "no backend" : backend.lastRefusal()));
        } catch (RuntimeException | LinkageError error) {
            celtium$noteDrawArrayRefusal("compiled client arrays: " + error);
        }
    }

    /** The index of the range containing {@code [start,end)}, or -1. */
    private static int celtium$inRange(ReadableRange[] ranges, long start, long end) {
        int low = 0;
        int high = ranges.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            ReadableRange range = ranges[mid];
            if (end <= range.start) {
                high = mid - 1;
            } else if (start >= range.end) {
                low = mid + 1;
            } else if (start >= range.start && end <= range.end) {
                return mid;
            } else {
                // Overlaps the mapping but is not contained in it: straddles two mappings, which is not readable as
                // one contiguous run.
                return -1;
            }
        }
        return -1;
    }

    /** One readable mapping: {@code [start, end)}. */
    private static final class ReadableRange {
        final long start;
        final long end;

        ReadableRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    /** The cached mapping list, refreshed on a miss; volatile because the list is replaced, never mutated. */
    private volatile ReadableRange[] readableRanges;
    /** How often the list has been re-read, and when the last read happened: a bound on the refresh rate. */
    private int readableMappingRefreshes;
    private long readableMappingLastRefreshNanos;
    private static final int READABLE_MAPPING_REFRESH_LIMIT = 64;
    private static final long READABLE_MAPPING_REFRESH_INTERVAL_NANOS = 100L * 1000L * 1000L;

    private ReadableRange[] celtium$refreshReadableMappings() {
        long now = System.nanoTime();
        if (this.readableMappingRefreshes >= READABLE_MAPPING_REFRESH_LIMIT
                || now - this.readableMappingLastRefreshNanos < READABLE_MAPPING_REFRESH_INTERVAL_NANOS) {
            ReadableRange[] current = this.readableRanges;
            return current == null ? new ReadableRange[0] : current;
        }
        this.readableMappingRefreshes++;
        this.readableMappingLastRefreshNanos = now;
        java.util.List<ReadableRange> ranges = new java.util.ArrayList<ReadableRange>();
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/proc/self/maps"), "US-ASCII"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    // "7f0a1c000000-7f0a1c021000 rw-p 00000000 00:00 0 ..." - the permission field is the second.
                    int dash = line.indexOf('-');
                    int space = line.indexOf(' ');
                    if (dash <= 0 || space <= dash + 1 || space + 1 >= line.length()) {
                        continue;
                    }
                    if (line.charAt(space + 1) != 'r') {
                        continue;
                    }
                    long start = Long.parseUnsignedLong(line.substring(0, dash).trim(), 16);
                    long stop = Long.parseUnsignedLong(line.substring(dash + 1, space).trim(), 16);
                    if (stop > start) {
                        ranges.add(new ReadableRange(start, stop));
                    }
                }
            } finally {
                reader.close();
            }
        } catch (java.io.IOException | RuntimeException error) {
            readableMappingFailure = String.valueOf(error);
            return new ReadableRange[0];
        }
        java.util.Collections.sort(ranges, new java.util.Comparator<ReadableRange>() {
            @Override
            public int compare(ReadableRange left, ReadableRange right) {
                return Long.compare(left.start, right.start);
            }
        });
        ReadableRange[] array = ranges.toArray(new ReadableRange[0]);
        this.readableRanges = array;
        readableMappingRanges = array.length;
        return array;
    }

    /**
     * Diagnostics for the mapping guard: how many client-memory draws it let through, how many readable ranges the
     * last read of {@code /proc/self/maps} found, and why that read failed if it did. Static because the census line is
     * static, and one process has one mapping list.
     * <p>
     * The mapping guard is a <em>guard</em>, not an ownership record: it answers "is this range readable right now",
     * not "who owns it, how long does it live, or is it still the buffer the game meant". It is also Linux-only. The
     * authoritative client-memory entry point is the owned-buffer publication below; these two counters exist to keep
     * the two apart in a run.
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
        String key = this.vertexPointerStride + "/" + formatId + "/" + celtium$otherArrayMask();
        if (!CLIENT_TUPLES.add(key)) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_CLIENT_ARRAY stride=" + this.vertexPointerStride
                + " format=" + formatId + " otherArrays=" + celtium$otherArrayNames()
                + " count=" + count + " pointer=0x" + Long.toHexString(this.vertexPointerOffset)
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
    private static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_PURE_BLUE =
            new java.util.concurrent.atomic.AtomicLong();
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
            float[] colour = vulkan.celtium$mirroredColor();
            if (colour == null || colour[0] != 0.0F || colour[1] != 0.0F) {
                return;
            }
            DRAWARRAY_PURE_BLUE.incrementAndGet();
            if (DRAWARRAY_PURE_BLUE_LOGGED.incrementAndGet() <= 3) {
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
    /** Opt-in trace at the projection guard; pairs with the backend's CELTIUM_MATRIX_TRACE. */
    private static final boolean MATRIX_TRACE = Boolean.getBoolean("celtium.vulkan.matrixTrace");
    private static int projectionRefusalDumps;

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
    private static String celtium$callSite(int limit) {
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
            // The non-DSA form: (target, pname, out) applies to the texture bound to the target on the active unit.
            int target = (int) c[0];
            int paramPname = (int) c[1];
            int textureName = 0;
            if (!isProxyTarget(target)) {
                int bound = (target == GL_TEXTURE_2D ? this.textures.texture2d() : this.textureBindings.get(texBindKey(target)));
                textureName = bound < 0 ? 0 : bound;
            }
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
        if (fn.name.startsWith("glGetNamedFramebufferAttachmentParameter")
                || fn.name.startsWith("glGetFramebufferAttachmentParameter")) {
            // (target-or-fbo, attachment, pname, out). No framebuffer objects exist under the stub, so the honest
            // answer is "no attachment" rather than a zero that reads as attachment 0.
            memPutInt(out, 0); // GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE = GL_NONE: there is no attachment
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
            if (pname == 0x8764) { // GL_BUFFER_SIZE - LWJGL needs this to size glMapBuffer results
                int bound = boundBuffer((int) c[0]);
                NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(bound);
                ByteBuffer storage = bound == 0 ? null : this.bufferStore.get(bound);
                int size = nativeBuffer != null ? nativeBuffer.byteSize()
                        : logicalSizeOf(bound, storage == null ? 0 : storage.capacity());
                if ("glGetBufferParameteri64v".equals(fn.name)) {
                    memPutLong(out, size);
                } else {
                    memPutInt(out, size);
                }
                return;
            }
            memPutInt(out, 0);
            return;
        }
        if ("glGetShaderiv".equals(fn.name) || "glGetProgramiv".equals(fn.name)) {
            // GL_COMPILE_STATUS 0x8B81 / GL_LINK_STATUS 0x8B82 / GL_VALIDATE_STATUS 0x8B83 -> true;
            // everything else (log length, attached counts...) -> 0.
            memPutInt(out, pname >= 0x8B81 && pname <= 0x8B83 ? 1 : 0);
            return;
        }
        memPutInt(out, 0);
    }

    /** Info-log queries: write the empty string; the length out-arg gets the remainder the caller asked about. */
    private void infoLog(Fn fn, long[] c) {
        int last = lastPointerArg(fn);
        if (last < 0) {
            return;
        }
        long log = c[last];
        if (log != 0L) {
            MemoryUtil.memPutByte(log, (byte) 0);
        }
        // The optional length out is the pointer slot before the buffer, when it exists.
        for (int i = last - 1; i >= 0; i--) {
            if (fn.args[i] == A_PTR) {
                long lenOut = c[i];
                if (lenOut != 0L && lenOut != log) {
                    memPutInt(lenOut, 0);
                }
                break;
            }
        }
    }
}
