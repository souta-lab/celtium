package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;
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
 * The OpenGL function surface Minecraft 1.12.2 calls, answered without a GL context.
 * <p>
 * This is the seam a context-less window hangs on: {@code GL.create(provider)} accepts any
 * {@link FunctionProvider}, so the game's {@code GL11.*} calls can run against trampolines owned by the renderer
 * instead of a real driver. It is deliberately not a GL emulator - the semantic classes are the ones the game
 * actually uses:
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
 *   <li>Everything else records and no-ops - drawing, blending and state setting the Vulkan side already mirrors
 *   or owns, so dropping them loses nothing the frame needed.</li>
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
    private static final int SEM_NOOP = 0;            // record only - state the Vulkan side owns or nobody reads
    private static final int SEM_GEN = 1;             // object-name allocation: returns the next id
    private static final int SEM_GET_ERROR = 2;       // always GL_NO_ERROR - nothing the stub does can raise one
    private static final int SEM_GET_STRING = 3;      // arg0 pname -> interned ascii
    private static final int SEM_IS_ENABLED = 4;      // arg0 cap -> recorded enable bit
    private static final int SEM_ENABLE = 5;          // arg0 cap -> set enable bit
    private static final int SEM_DISABLE = 6;         // arg0 cap -> clear enable bit
    private static final int SEM_GET_INTEGERV = 7;    // arg0 pname, arg1 out[] -> plausible ints
    private static final int SEM_GET_FLOATV = 8;      // arg0 pname, arg1 out[] -> plausible floats
    private static final int SEM_GET_BOOLEANV = 9;    // arg0 pname, arg1 out[] -> plausible bytes
    private static final int SEM_IS_OBJECT = 10;      // arg0 id -> whether the table generated it
    private static final int SEM_DELETE = 11;         // id list -> retire names (reissue is the same counter-free)
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

    /** One function-table row: name, return type, arg types, semantics. */
    private static final class Fn {
        final String name;
        final byte ret;
        final byte[] args;
        final int semantics;

        Fn(String name, byte ret, byte[] args, int semantics) {
            this.name = name;
            this.ret = ret;
            this.args = args;
            this.semantics = semantics;
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
        fn("glDisable", R_VOID, SEM_DISABLE, A_INT);
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
        fn("glGetTexParameteriv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
        fn("glGetTexParameterfv", R_VOID, SEM_STATUS, A_INT, A_INT, A_PTR);
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
                "glBindSampler", "glBindBufferBase", "glBindBufferRange", "glBlendFunc",
                "glBlendFuncSeparate", "glBlendFuncSeparatei", "glBlendEquation", "glBlendEquationSeparate",
                "glBlendColor", "glColorMask", "glColorMaski", "glDepthMask", "glDepthFunc", "glDepthRange",
                "glClearBufferfv", "glClearBufferiv",
                "glClearBufferuiv", "glStencilFunc", "glStencilFuncSeparate", "glStencilOp", "glStencilOpSeparate",
                "glStencilMask", "glStencilMaskSeparate", "glCullFace", "glFrontFace", "glPolygonMode",
                "glPolygonOffset", "glPointSize", "glPrimitiveRestartIndex", "glProvokingVertex",
                "glSampleCoverage", "glSampleMaski", "glScissor", "glLogicOp", "glHint",
                "glPixelStoref", "glReadBuffer", "glDrawBuffer", "glDrawBuffers", "glClampColor",
                "glColorMaterial", "glMaterialf", "glMaterialfv", "glMateriali", "glLightf", "glLightfv",
                "glLighti", "glLightiv", "glLightModelf", "glLightModelfv", "glLightModeli", "glLightModeliv",
                "glShadeModel", "glFogf", "glFogfv", "glFogi", "glFogiv", "glTexEnvi", "glTexEnvf", "glTexEnviv",
                "glTexEnvfv", "glTexGeni", "glTexGenf", "glTexGeniv", "glTexGenfv", "glAlphaFunc", "glNormal3f",
                "glNormal3b", "glNormal3fv", "glColor4f", "glColor3f", "glColor4ub", "glColor3ub", "glSecondaryColor3f",
                "glTexCoord2f", "glMultiTexCoord2f", "glVertex3f", "glBegin", "glEnd", "glMatrixMode",
                "glLoadIdentity", "glLoadMatrixf", "glLoadMatrixd", "glMultMatrixf", "glMultMatrixd", "glPushMatrix",
                "glPopMatrix", "glOrtho", "glFrustum", "glTranslatef", "glTranslated", "glRotatef", "glRotated",
                "glScalef", "glScaled", "glTexParameteri", "glTexParameterf", "glTexParameteriv", "glTexParameterfv",
                "glTexParameterIiv", "glTexParameterIuiv", "glGenerateMipmap", "glSamplerParameteri",
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
                "glCopyPixels", "glDrawPixels", "glReadPixels", "glCopyTexImage2D", "glCopyTexSubImage2D",
                "glBlitFramebuffer", "glFramebufferTexture2D", "glFramebufferTexture", "glFramebufferTexture1D",
                "glFramebufferTexture3D", "glFramebufferTextureLayer", "glFramebufferRenderbuffer",
                "glRenderbufferStorage", "glRenderbufferStorageMultisample",
                "glTexSubImage1D", "glTexSubImage3D",
                "glCompressedTexSubImage2D", "glGetTexImage",
                "glGetCompressedTexImage", "glMapBuffer", "glMapBufferRange",
                "glCopyBufferSubData", "glFlushMappedBufferRange", "glGetBufferSubData",
                "glTexCoordPointer", "glColorPointer", "glNormalPointer", "glIndexPointer", "glEdgeFlagPointer",
                "glSecondaryColorPointer", "glFogCoordPointer", "glInterleavedArrays", "glArrayElement",
                "glDrawBuffer", "glRects", "glClipPlane", "glTexEnv", "glSecondaryColor3fv", "glColor4fv",
                "glDepthBoundsEXT", "glMultiDrawArraysIndirect", "glMultiDrawElementsIndirect", "glDrawArraysIndirect",
                "glDrawElementsIndirect", "glDispatchCompute", "glMemoryBarrier", "glTextureBarrier",
                "glFramebufferParameteri", "glNamedFramebufferTexture",
                "glNamedFramebufferRenderbuffer", "glNamedRenderbufferStorage",
                "glTextureImage2DEXT",
                "glTextureSubImage2D", "glNamedTexImage2DEXT", "glNamedCopyTexSubImage2DEXT", "glTextureStorage2D",
                "glTextureParameteriv", "glTextureParameteri", "glGenerateTextureMipmap", "glBindTextureUnit",
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
    private final java.util.concurrent.atomic.AtomicInteger nextName = new java.util.concurrent.atomic.AtomicInteger(1);
    private final java.util.concurrent.atomic.AtomicLong nextSync = new java.util.concurrent.atomic.AtomicLong(1);
    private final java.util.Set<Long> liveSync = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Integer> liveNames = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<Integer, Boolean> enables = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, Integer> bufferBindings = new java.util.concurrent.ConcurrentHashMap<>();
    /** Bound texture name per (active unit, target). The game uses one unit; the key keeps the option open. */
    private final java.util.Map<Long, Integer> textureBindings = new java.util.concurrent.ConcurrentHashMap<>();
    private int activeTexture;
    /** Recorded image extents: key {@code (textureName << 16) | level}, proxy targets key on a name of -target. */
    private final java.util.Map<Long, int[]> texLevels = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Captured uploads in RGBA8, keyed like {@link #texLevels}. A full {@code glTexImage*} publishes a new record, so
     * a reader sees the old image or the new one and never a half-written one; {@code glTexSubImage2D} patches rows
     * of the record that is already there.
     */
    private final java.util.Map<Long, TexCapture> texCaptures = new java.util.concurrent.ConcurrentHashMap<>();
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
     * The unpack state {@code glPixelStorei} set. Conversion reads rows where GL would read them: assuming tight rows
     * while the game asked for a row length or a row skip would store a sheared image instead of a missing one.
     */
    private volatile int unpackAlignment = 4;
    private volatile int unpackRowLength;
    private volatile int unpackSkipPixels, unpackSkipRows;
    private final java.util.Map<Integer, ByteBuffer> bufferStore = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, Integer> lastBoundOnTarget = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<Callback> ownedTrampolines = new ArrayList<>();
    private long getStringAddress;
    private ByteBuffer getStringBuffer;
    private volatile int viewportW = 1280, viewportH = 720;
    /** GL_DRAW_FRAMEBUFFER / GL_READ_FRAMEBUFFER bindings; GL_FRAMEBUFFER sets both. */
    private volatile int drawFbo, readFbo;
    /** The colour and depth glClear will use, and the buffers it was asked to clear and nobody consumed yet. */
    private volatile float clearRed, clearGreen, clearBlue = 0.0f, clearAlpha = 1.0f;
    private volatile double clearDepth = 1.0;
    private final java.util.concurrent.atomic.AtomicInteger pendingClears =
            new java.util.concurrent.atomic.AtomicInteger();
    /** Fixed-pipeline state the getters answer from, at the values a fresh GL context starts them at. */
    private volatile float currentRed = 1.0f, currentGreen = 1.0f, currentBlue = 1.0f, currentAlpha = 1.0f;
    private volatile float lineWidth = 1.0f;
    /** The program glUseProgram bound, the display list being compiled, and the bound vertex array. */
    private volatile int currentProgram, compilingList, boundVertexArray, lastCalledList;
    /**
     * The client-array pointer state: {@code glVertexPointer(size, type, stride, pointer)}. GL keeps exactly one of
     * these, and it is the only thing that says how the bound array buffer's bytes are to be read - the bytes alone
     * are anonymous. Recorded rather than no-opped for the same reason the buffer storage is mirrored: a draw that
     * arrives without it cannot be decoded, and guessing a layout would submit the wrong bytes rather than none.
     */
    private volatile int vertexPointerSize, vertexPointerType, vertexPointerStride;
    private volatile long vertexPointerOffset;

    /** The installed stub - one per process; GL capabilities are process-global so this is a singleton. */
    private static CeltiumGlStub installed;

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
                if (DRAWARRAY_CALLS.get() > 0L) {
                    celtium$printAtExit("CELTIUM_DRAWARRAYS " + drawArraySummary());
                }
            }, "celtium-noop-census"));
        }
        return installed;
    }

    /**
     * Whether this stub answers GL calls in this process. When it does there is no GL device identity to read -
     * the renderer's strings are ours, not a driver's.
     */
    public static boolean isInstalled() {
        return installed != null;
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
    public static int consumePendingClears() {
        CeltiumGlStub stub = installed;
        return stub == null ? 0 : stub.pendingClears.getAndSet(0);
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
                : stub.texCaptures.get(Long.valueOf(texLevelKey(textureName, level)));
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
    public static boolean textureLevelHasGamePixels(int textureName, int level) {
        CeltiumGlStub stub = installed;
        if (stub == null) {
            return false;
        }
        TexCapture capture = stub.texCaptures.get(Long.valueOf(texLevelKey(textureName, level)));
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
        return "texture capture: levels=" + stub.texCaptures.size()
                + " full=" + stub.fullCaptures.get()
                + " patches=" + stub.subCaptures.get()
                + " undefined=" + stub.undefinedExtents.get()
                + " unconverted=" + stub.unconvertedUploads.get()
                + " bytes=" + bytes;
    }

    private CeltiumGlStub() {
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
        if (Boolean.getBoolean("celtium.glstub.trace")
                && ("glGetError".equals(name) || "glGetIntegerv".equals(name) || "glGetString".equals(name))) {
            System.out.println("[GLSTUB] addressFor " + name + " id=" + id);
        }
        byte[] args = id >= 0 ? TABLE.get(id).args : new byte[0];
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
        return SIGNATURE_DESCRIPTORS.computeIfAbsent(key.toString(), ignored -> {
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
            return new Callback.Descriptor(java.lang.invoke.MethodHandles.lookup(),
                    apiCreateCIF(ffi_type_pointer, types.toArray(new FFIType[0])));
        });
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
            java.lang.invoke.MethodHandles.lookup(),
            apiCreateCIF(ffi_type_pointer, new FFIType[] {
                    ffi_type_float, ffi_type_float, ffi_type_float, ffi_type_float,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer }));

    private static final Callback.Descriptor DOUBLE1_DESCRIPTOR = new Callback.Descriptor(
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
    private static final class CeltiumGlTrampoline extends Callback implements GlTrampolineI {
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
        long[] c = new long[SLOTS];
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
        APIUtil.apiClosureRetP(result, dispatch(functionId,
                c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10], c[11]));
    }

    /**
     * Handles a call. {@code c} is the call's argument values in order - ints widened to longs, pointers as raw
     * addresses. Only the first {@code fn.args.length} slots are meaningful: the rest carry whatever the upcall read
     * past the arguments the C caller actually passed (or an explicit zero from the float/double entries). The return
     * value goes into the caller's result register; narrower results use the low bits.
     */
    private long dispatch(int functionId, long c0, long c1, long c2, long c3, long c4, long c5, long c6, long c7,
            long c8, long c9, long c10, long c11) {
        if (functionId < 0) {
            // A function outside the table: the generic no-op.
            return 0L;
        }
        Fn fn = TABLE.get(functionId);
        if (Boolean.getBoolean("celtium.glstub.trace") && this.dispatchCount.incrementAndGet() < TRACE_LIMIT) {
            System.out.println("[GLSTUB] #" + this.dispatchCount.get() + " " + fn.name
                    + " (" + Long.toHexString(c0) + "," + Long.toHexString(c1) + "," + Long.toHexString(c2)
                    + "," + Long.toHexString(c3) + ")");
        }
        long[] c = { c0, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11 };
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
                return 1L; // GL_TRUE - contents already live in the storage
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
                return this.enables.getOrDefault(Integer.valueOf((int) a0), Boolean.FALSE)
                        .booleanValue() ? 1L : 0L;
            case SEM_ENABLE:
                this.enables.put(Integer.valueOf((int) a0), Boolean.TRUE);
                return 0L;
            case SEM_DISABLE:
                this.enables.put(Integer.valueOf((int) a0), Boolean.FALSE);
                return 0L;
            case SEM_GET_INTEGERV:
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
                return this.liveNames.contains(Integer.valueOf((int) a0))
                        || this.liveSync.contains(Long.valueOf(a0)) ? 1L : 0L;
            case SEM_DELETE:
                delete(fn, c);
                return 0L;
            case SEM_BIND_BUFFER:
                this.bufferBindings.put(Integer.valueOf((int) a0), Integer.valueOf((int) a1));
                return 0L;
            case SEM_BIND_TEXTURE:
                this.textureBindings.put(texBindKey((int) a0), Integer.valueOf((int) a1));
                return 0L;
            case SEM_ACTIVE_TEXTURE:
                // The unit index, not the raw enum: the getter adds the GL_TEXTURE0 base back, and a stored enum made
                // every save/restore round trip add another base (measured as the 0x84C0 ladder in the game's own
                // active-unit reads), which sent every unit-keyed binding record to a key no reader looked up.
                this.activeTexture = Math.max(0, (int) a0 - 0x84C0);
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
                this.viewportW = (int) c[2];
                this.viewportH = (int) c[3];
                return 0L;
            case SEM_BIND_FRAMEBUFFER:
                bindFramebuffer((int) a0, (int) a1);
                return 0L;
            case SEM_CLEAR:
                this.pendingClears.getAndUpdate(mask -> mask | (int) a0);
                return 0L;
            case SEM_CLEAR_COLOR:
                this.clearRed = Float.intBitsToFloat((int) c[0]);
                this.clearGreen = Float.intBitsToFloat((int) c[1]);
                this.clearBlue = Float.intBitsToFloat((int) c[2]);
                this.clearAlpha = Float.intBitsToFloat((int) c[3]);
                return 0L;
            case SEM_CLEAR_DEPTH:
                this.clearDepth = "glClearDepth".equals(fn.name) ? Double.longBitsToDouble(c[0])
                        : (double) Float.intBitsToFloat((int) c[0]);
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
                this.compilingList = (int) c[0];
                return 0L;
            case SEM_END_LIST:
                this.compilingList = 0;
                return 0L;
            case SEM_CALL_LIST:
                // Only the name is recorded; nothing compiled inside the list is replayed, so under GL-less every
                // glCallList draws nothing. That is a named gap rather than a silent one, and it is the reason the sky
                // has two paths in RenderGlobal.renderSky: with `vboEnabled` (OpenGlHelper.useVbo(), true in this
                // fixture, measured as the VBO branch of RenderGlobal.func_174976_a:1345/1442/1471 in the vertex-pointer
                // census) the dome, the stars and the horizon plane arrive as glDrawArrays and are decoded below; with
                // it false they arrive as glCallList(glSkyList/starGLCallList/glSkyList2) and are drawn by nobody here.
                // Replaying a list would mean recording its calls per list id at glNewList..glEndList, which is a
                // different piece of work from this decode and is not exercised by any run yet.
                this.lastCalledList = (int) c[0];
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
                if (VERTEX_POINTER_SITES.size() < 12
                        && VERTEX_POINTER_SITES.add(Integer.valueOf((int) c[0] * 31 + (int) c[1] * 7 + (int) c[2]))) {
                    System.out.println("[Celtium] CELTIUM_VERTEXPOINTER size=" + (int) c[0] + " type=0x"
                            + Integer.toHexString((int) c[1]) + " stride=" + (int) c[2] + " offset=" + c[3]
                            + " site=[" + celtium$callSite(8) + "]");
                }
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

    /** Every no-op entry point that was actually called, by name. Bounded by the table's size. */
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> NOOP_CALLS =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();

    private static void celtium$noteNoop(String name) {
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
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>> entries =
                new java.util.ArrayList<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>>(
                        NOOP_CALLS.entrySet());
        entries.sort((left, right) -> Long.compare(right.getValue().get(), left.getValue().get()));
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (int i = 0; i < entries.size() && i < limit; ++i) {
            out.add(entries.get(i).getKey() + "=" + entries.get(i).getValue().get());
        }
        return out;
    }

    /** {@return the number of distinct no-op entry points called, and the total number of calls}. */
    public static String noopCensusSummary() {
        long total = 0L;
        for (java.util.concurrent.atomic.AtomicLong counter : NOOP_CALLS.values()) {
            total += counter.get();
        }
        return "noopEntryPoints=" + NOOP_CALLS.size() + " noopCalls=" + total
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
                + " first=[" + drawArrayFirst + "]"
                + " refusedByReason=[" + (reasons.length() == 0 ? "none" : reasons.toString()) + "]";
    }

    private static void celtium$noteMirrorMiss(String name) {
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
     */
    public static String stubTransformSummary() {
        if (MIRROR_MISSES.isEmpty()) {
            return "none";
        }
        StringBuilder line = new StringBuilder();
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry
                : new java.util.TreeMap<String, java.util.concurrent.atomic.AtomicLong>(MIRROR_MISSES).entrySet()) {
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(entry.getKey()).append('=').append(entry.getValue().get());
        }
        return line.toString();
    }

    private long generate(Fn fn, long[] c) {
        if (fn.ret == R_INT && fn.args.length <= 1 && lastPointerArg(fn) < 0) {
            // Scalar-return form (glCreateShader, glCreateProgram, glGenLists): the id is the return value.
            int scalar = this.nextName.getAndIncrement();
            this.liveNames.add(Integer.valueOf(scalar));
            return scalar;
        }
        if (fn.ret == R_LONG) {
            // Sync object (glFenceSync): ids come from the 64-bit space.
            long sync = this.nextSync.getAndIncrement();
            this.liveSync.add(Long.valueOf(sync));
            this.liveNames.add(Integer.valueOf((int) sync));
            return sync;
        }
        // Array form (glGenTextures, glCreateTextures...): the out[] is the LAST pointer arg; the count is the
        // int immediately before it.
        int outIndex = lastPointerArg(fn);
        if (outIndex < 0) {
            int scalar = this.nextName.getAndIncrement();
            this.liveNames.add(Integer.valueOf(scalar));
            return scalar;
        }
        long out = c[outIndex];
        if (out == 0L) {
            return 0L;
        }
        int n = countFromArgs(fn, c, outIndex);
        for (int i = 0; i < n; i++) {
            int name = this.nextName.getAndIncrement();
            this.liveNames.add(Integer.valueOf(name));
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
            for (int i = 0; i < count; i++) {
                int name = memGetInt(names + (long) i * 4L);
                this.liveNames.remove(Integer.valueOf(name));
                if (textures) {
                    forgetTexture(name);
                }
            }
            return;
        }
        if (fn.args.length == 1) {
            if (fn.args[0] == A_LONG) {
                this.liveSync.remove(Long.valueOf(c[0]));
            } else {
                this.liveNames.remove(Integer.valueOf((int) c[0]));
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
    private void forgetTexture(int name) {
        for (int level = 0; level < 256; level++) {
            Long key = Long.valueOf(texLevelKey(name, level));
            this.texLevels.remove(key);
            this.texCaptures.remove(key);
        }
    }

    // ---- state queries ---------------------------------------------------------------

    private long stringFor(int pname) {
        String value;
        switch (pname) {
            case 0x1F00: // GL_VENDOR
                value = "Celtium";
                break;
            case 0x1F01: // GL_RENDERER
                value = "Celtium stub GL";
                break;
            case 0x1F02: // GL_VERSION
                value = "4.6.0 Celtium";
                break;
            case 0x1F03: // GL_EXTENSIONS
                value = "";
                break;
            case 0x8B8C: // GL_SHADING_LANGUAGE_VERSION
                value = "4.60";
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

    private void getInts(int pname, long out, Fn fn) {
        if (out == 0L) {
            return;
        }
        int value;
        switch (pname) {
            case 0x0BA2: // GL_VIEWPORT
                memPutInt(out, 0);
                memPutInt(out + 4, 0);
                memPutInt(out + 8, this.viewportW);
                memPutInt(out + 12, this.viewportH);
                return;
            case 0x8069: { // GL_TEXTURE_BINDING_2D - the name bound to TEXTURE_2D on the active unit
                Integer bound = this.textureBindings.get(Long.valueOf(texBindKey(0x0DE1)));
                value = bound == null ? 0 : bound.intValue();
                break;
            }
            case 0x84E0: // GL_ACTIVE_TEXTURE
                value = 0x84C0 + this.activeTexture;
                break;
            case 0x8CA6: // GL_DRAW_FRAMEBUFFER_BINDING / GL_FRAMEBUFFER_BINDING
                value = this.drawFbo;
                break;
            case 0x8CA5: // GL_READ_FRAMEBUFFER_BINDING
                value = this.readFbo;
                break;
            case 0x0C10: // GL_SCISSOR_BOX - four ints, and the default is the whole drawable
                memPutInt(out, 0);
                memPutInt(out + 4, 0);
                memPutInt(out + 8, this.viewportW);
                memPutInt(out + 12, this.viewportH);
                return;
            case 0x0B33: // GL_LIST_INDEX - the display list being compiled, which the model path keys on
                value = this.compilingList;
                break;
            case 0x8B8D: // GL_CURRENT_PROGRAM
                value = this.currentProgram;
                break;
            case 0x0B74: // GL_DEPTH_FUNC
                value = 0x0201; // GL_LESS
                break;
            case 0x0D05: // GL_PACK_ALIGNMENT
                value = 4;
                break;
            case 0x0B65: // GL_FOG_MODE
                value = 0x2601; // GL_LINEAR
                break;
            case 0x85B5: // GL_VERTEX_ARRAY_BINDING
                value = this.boundVertexArray;
                break;
            case 0x0D33: // GL_MAX_TEXTURE_SIZE
                value = 16384;
                break;
            case 0x8869: // GL_MAX_VERTEX_ATTRIBS
                value = 16;
                break;
            case 0x821B: // GL_MAJOR_VERSION
                value = 4;
                break;
            case 0x821C: // GL_MINOR_VERSION
                value = 6;
                break;
            case 0x821D: // GL_NUM_EXTENSIONS
                value = 0;
                break;
            case 0x8513: // GL_TEXTURE_MAX_ANISOTROPY samples? actually MAX_TEXTURE_MAX_ANISOTROPY is 0x84FF
                value = 16;
                break;
            case 0x84FF:
                value = 16;
                break;
            case 0x8B4D: // GL_MAX_TEXTURE_IMAGE_UNITS
                value = 16;
                break;
            case 0x8872: // GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS
                value = 32;
                break;
            default:
                value = 0;
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
     * transform built from it was garbage. Matrices answer the identity and the single-valued states answer the value
     * a fresh GL context starts them at, which is the honest answer for a context that never had them set.
     */
    private void getFloats(int pname, long out, Fn fn) {
        if (out == 0L) {
            return;
        }
        boolean asDouble = "glGetDoublev".equals(fn.name);
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
        switch (pname) {
            case 0x0BA6:
                return vulkan.celtium$mirroredModelView();
            case 0x0BA7:
                return vulkan.celtium$mirroredProjection();
            case 0x0BA8:
                return vulkan.celtium$mirroredTexture();
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
        if (out != 0L) {
            MemoryUtil.memPutByte(out, (byte) 0);
        }
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
    }

    private static boolean isProxyTarget(int target) {
        // GL_PROXY_TEXTURE_1D/2D/3D/CUBE_MAP - uploads to these carry no pixels and bind to nothing.
        return target == 0x8063 || target == 0x8064 || target == 0x8070 || target == 0x851B;
    }

    private long texBindKey(int target) {
        return ((long) this.activeTexture << 16) | (target & 0xFFFFL);
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
        Integer bound = this.textureBindings.get(Long.valueOf(texBindKey(target)));
        return bound == null ? 0 : bound.intValue();
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
        this.texLevels.put(Long.valueOf(texLevelKey(name, level)), new int[] { width, height, internal });
    }

    /**
     * {@code glTexStorage2D/3D}: storage is reserved for level 0 (the level count is not walked) and its content is
     * undefined even in real GL, so there is nothing to capture - only the extent the size queries answer from.
     */
    private void texImage(long[] c) {
        recordExtent((int) c[0], 0, (int) c[2], (int) c[3], (int) c[4]);
    }

    /** Writes into the storage {@code glBufferData} allocated, which is what the mirrored meshes are read from. */
    private void bufferSubData(Fn fn, long[] c) {
        int name = this.bufferBindings.getOrDefault(Integer.valueOf((int) c[0]), Integer.valueOf(0)).intValue();
        long offset = c[1];
        long size = c[2];
        long data = c[3];
        ByteBuffer storage = this.bufferStore.get(Integer.valueOf(name));
        if (storage == null || data == 0L || size <= 0 || offset < 0 || offset + size > storage.capacity()) {
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

    /**
     * The largest level worth mirroring: 4096x4096 RGBA8, the biggest atlas the game builds. A larger upload is
     * counted as unconverted rather than allocated - one level of a speculative 16384x16384 probe would be a gigabyte
     * of direct memory for pixels the game itself never had to find room for.
     */
    private static final long MAX_CAPTURE_BYTES = 64L * 1024L * 1024L;

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

        TexCapture(int width, int height, ByteBuffer data, boolean gamePixels) {
            this.width = width;
            this.height = height;
            this.data = data;
            this.gamePixels = gamePixels;
        }
    }

    /**
     * {@code glTexImage1D/2D/3D}: the extent record plus the uploaded pixels, converted to RGBA8 and filed under the
     * bound texture's level. A NULL data pointer is a legal GL call that allocates the level with undefined content -
     * see {@link #undefinedLevel}, because the game fills levels exactly that way.
     */
    private void texImagePixels(Fn fn, long[] c) {
        boolean oneD = "glTexImage1D".equals(fn.name);
        boolean threeD = "glTexImage3D".equals(fn.name);
        int target = (int) c[0];
        int level = (int) c[1];
        int internal = (int) c[2];
        int width = (int) c[3];
        int height = oneD ? 1 : (int) c[4];
        int depth = threeD ? (int) c[5] : 1;
        // The format slot follows the extent, which differs per form: 1D has no height, 3D carries depth.
        int formatIndex = oneD ? 5 : threeD ? 7 : 6;
        int format = (int) c[formatIndex];
        int type = (int) c[formatIndex + 1];
        long pixels = c[formatIndex + 2];
        recordExtent(target, level, internal, width, height);
        int name = uploadTargetName(target);
        if (isProxyTarget(target) || name == 0) {
            return;
        }
        if (pixels == 0L) {
            undefinedLevel(name, level, width, height, depth);
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
        this.texCaptures.put(Long.valueOf(texLevelKey(name, level)), new TexCapture(width, height, rgba, true));
        this.fullCaptures.incrementAndGet();
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
        long bytes = (long) width * (long) height * 4L;
        if (depth > 1 || width <= 0 || height <= 0 || bytes > MAX_CAPTURE_BYTES) {
            // No shape to lay a surface out from: a 2D record cannot describe depth, and this level is past the size
            // the stub will mirror at all. The extent stands, the pixels do not.
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        ByteBuffer zeros = ByteBuffer.allocateDirect((int) bytes);
        // allocateDirect promises storage, not zeroed storage: HotSpot can hand back recycled memory for a small
        // buffer, and a garbage surface is exactly the silently wrong atlas this path exists to prevent.
        MemoryUtil.memSet(memAddress(zeros), 0, bytes);
        this.texCaptures.put(Long.valueOf(texLevelKey(name, level)), new TexCapture(width, height, zeros, false));
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
        int target = (int) c[0];
        if (isProxyTarget(target)) {
            return;
        }
        int level = (int) c[1];
        int x = (int) c[2];
        int y = (int) c[3];
        int width = (int) c[4];
        int height = (int) c[5];
        int format = (int) c[6];
        int type = (int) c[7];
        long pixels = c[8];
        int name = uploadTargetName(target);
        if (name == 0 || pixels == 0L || width <= 0 || height <= 0) {
            return;
        }
        ByteBuffer patch = uploadToRgba8(width, height, format, type, pixels);
        if (patch == null) {
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        long key = texLevelKey(name, level);
        TexCapture existing = this.texCaptures.get(Long.valueOf(key));
        if (existing == null) {
            this.texCaptures.put(Long.valueOf(key), new TexCapture(width, height, patch, true));
            this.subCaptures.incrementAndGet();
            return;
        }
        if (x < 0 || y < 0 || x + width > existing.width || y + height > existing.height) {
            this.unconvertedUploads.incrementAndGet();
            return;
        }
        int destinationStride = existing.width * 4;
        int sourceStride = width * 4;
        for (int row = 0; row < height; row++) {
            // Absolute and whole-row, so the record's own position never moves under a concurrent reader.
            existing.data.put((y + row) * destinationStride + x * 4, patch, row * sourceStride, sourceStride);
        }
        // The surface now holds game bytes even when it started as an undefined allocation: the level is what GL has
        // (undefined elsewhere, this patch here), so a mirror may be built from it. Set after the copy, so a reader
        // that sees the flag also sees the rows.
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
     * {@code GL_UNSIGNED_INT_8_8_8_8_REV} - the packed {@code 0xAARRGGBB} form the atlas uploads use - and
     * {@code GL_RGB} with {@code GL_UNSIGNED_BYTE}. Rows are read where GL would read them, padded to the recorded
     * unpack alignment; a row length or a pixel/row skip that is not the default is refused rather than misread.
     */
    private ByteBuffer uploadToRgba8(int width, int height, int format, int type, long pixels) {
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
            for (int row = 0; row < height; row++) {
                int base = row * stride;
                if (!bgra) {
                    // Absolute on both sides: the row's source offset and the record's offset move together, and no
                    // buffer position has to be legal at any intermediate point.
                    out.put(row * rowBytes, source, base, rowBytes);
                } else {
                    for (int i = 0; i < rowBytes; i += 4) {
                        int pixel = base + i;
                        out.put(source.get(pixel + 2)).put(source.get(pixel + 1))
                                .put(source.get(pixel)).put(source.get(pixel + 3));
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
        if (type == GL_UNSIGNED_INT_8_8_8_8_REV && (format == GL_RGBA || bgra)) {
            // The packed form puts the first component in the least significant bits of the 32-bit word, so the word
            // is read in host order and unpacked: for GL_BGRA that is the game's own 0xAARRGGBB int.
            int stride = aligned(width * 4, this.unpackAlignment);
            ByteBuffer source = MemoryUtil.memByteBuffer(pixels, (height - 1) * stride + width * 4)
                    .order(java.nio.ByteOrder.nativeOrder());
            ByteBuffer out = ByteBuffer.allocateDirect((int) bytes);
            for (int row = 0; row < height; row++) {
                int base = row * stride;
                for (int x = 0; x < width; x++) {
                    int packed = source.getInt(base + x * 4);
                    int first = packed & 0xFF;
                    int third = (packed >>> 16) & 0xFF;
                    out.put((byte) (bgra ? third : first)).put((byte) ((packed >>> 8) & 0xFF))
                            .put((byte) (bgra ? first : third)).put((byte) ((packed >>> 24) & 0xFF));
                }
            }
            out.clear();
            return out;
        }
        return null;
    }

    /** {@code bytes} rounded up to a GL unpack alignment, which is always a power of two: 1, 2, 4 or 8. */
    private static int aligned(int bytes, int alignment) {
        return (bytes + alignment - 1) & ~(alignment - 1);
    }

    /** The pname's recorded value for a named texture's level - the DSA form of {@link #texLevelParam}. */
    private int texLevelParamForName(int name, int level, int pname) {
        int[] dims = this.texLevels.get(Long.valueOf(texLevelKey(name, level)));
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
        int[] dims = this.texLevels.get(Long.valueOf(texLevelKey(name, level)));
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
    private int bufferFor(Fn fn, long[] c) {
        if ("glNamedBufferData".equals(fn.name) || "glNamedBufferStorage".equals(fn.name)) {
            return (int) c[0];
        }
        Integer bound = this.bufferBindings.get(Integer.valueOf((int) c[0]));
        return bound == null ? 0 : bound.intValue();
    }

    private void bufferData(Fn fn, long[] c) {
        int name = bufferFor(fn, c);
        long size = c[1];
        long data = c[2];
        if (name == 0 || size <= 0 || size > Integer.MAX_VALUE) {
            return;
        }
        // The storage is reused whenever it is already large enough, rather than replaced. The game's staging buffers
        // are mapped persistently - the mirror this renderer reads its meshes from is one - and a new allocation moves
        // the address the caller is still writing to, which shows up as geometry built from whatever the old storage
        // happened to hold. Keeping the block makes the address a buffer name returns stable for the name's lifetime,
        // which is the property a persistent mapping needs; a larger request still has to move it.
        ByteBuffer storage = this.bufferStore.get(Integer.valueOf(name));
        if (storage == null || storage.capacity() < size) {
            storage = ByteBuffer.allocateDirect((int) size);
            this.bufferStore.put(Integer.valueOf(name), storage);
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

    private long mapBuffer(Fn fn, long[] c) {
        int name;
        if (fn.name.startsWith("glMapNamedBuffer")) {
            name = (int) c[0];
        } else {
            Integer bound = this.bufferBindings.get(Integer.valueOf((int) c[0]));
            name = bound == null ? 0 : bound.intValue();
        }
        ByteBuffer storage = this.bufferStore.get(Integer.valueOf(name));
        if (storage == null) {
            // Mapping a buffer with no allocation: GL would fault - an empty block keeps the caller alive while
            // making writes visibly land nowhere.
            storage = ByteBuffer.allocateDirect(0);
        }
        long base = memAddress(storage);
        if (fn.args.length == 4) {
            // glMapBufferRange(target, offset, length, access)
            long offset = c[1];
            if (offset < 0 || offset >= storage.capacity()) {
                offset = 0L;
            }
            return base + offset;
        }
        return base;
    }

    // ---- client-array drawing: the one GL drawing entry point that leaves this class for the frame ---------------

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
    private void drawArrays(int mode, int first, int count) {
        DRAWARRAY_CALLS.incrementAndGet();
        if (count <= 0) {
            // GL draws nothing for an empty range, and so does this: counting it as a gap would be a lie.
            DRAWARRAY_EMPTY.incrementAndGet();
            celtium$reportDrawArrays();
            return;
        }
        Integer boundName = this.bufferBindings.get(Integer.valueOf(GL_ARRAY_BUFFER));
        int buffer = boundName == null ? 0 : boundName.intValue();
        ByteBuffer storage = buffer == 0 ? null : this.bufferStore.get(Integer.valueOf(buffer));
        long offset = this.vertexPointerOffset;
        long end = 0L;
        String reason = null;
        if (storage == null) {
            reason = buffer == 0
                    ? "no GL_ARRAY_BUFFER is bound (the pointer would be client memory, which the stub does not mirror)"
                    : "the bound array buffer " + buffer + " has no mirrored storage";
        } else if (!this.enables.getOrDefault(Integer.valueOf(GL_VERTEX_ARRAY), Boolean.FALSE).booleanValue()) {
            reason = "GL_VERTEX_ARRAY is not enabled, so no vertex pointer describes this draw";
        } else if (mode != GL_QUADS) {
            reason = "draw mode " + mode + " is not handled (only 7 = GL_QUADS)";
        } else if (this.vertexPointerSize != 3 || this.vertexPointerType != GL_FLOAT
                || this.vertexPointerStride != POSITION_STRIDE) {
            // The pointer state is the contract: format 7 is three floats per vertex, and anything else is a
            // different format whose bytes read as positions would be wrong rather than absent.
            reason = "vertex pointer size=" + this.vertexPointerSize + " type=0x"
                    + Integer.toHexString(this.vertexPointerType) + " stride=" + this.vertexPointerStride
                    + " is not the position-only float3/" + POSITION_STRIDE + " this path decodes";
        } else {
            String extra = celtium$otherClientArrays();
            if (extra != null) {
                // Those arrays are enabled, so GL would have drawn attributes this format does not carry. Refused:
                // an absent sky is visible, a sky drawn from the wrong offsets is not.
                reason = "client arrays are enabled that format 7 does not read: " + extra;
            }
        }
        if (reason == null && (first < 0 || offset < 0L)) {
            reason = "first=" + first + " and pointer offset=" + offset + " must not be negative";
        }
        if (reason == null) {
            end = offset + (long) first * POSITION_STRIDE + (long) count * POSITION_STRIDE;
            if (end > storage.capacity()) {
                reason = "the draw reads to byte " + end + " of a " + storage.capacity() + "-byte buffer";
            }
        }
        long address = 0L;
        if (reason == null) {
            address = memAddress(storage, 0) + offset + (long) first * POSITION_STRIDE;
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
                reason = celtium$projectionRefusal(backend);
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
                    taken = backend.submitImmediateBatch(null, POSITION_STRIDE, VERTEX_FORMAT_POSITION, address,
                            count, mode);
                } catch (RuntimeException | LinkageError error) {
                    taken = false;
                    reason = "the backend threw: " + error;
                }
                if (taken) {
                    DRAWARRAY_DRAWN.incrementAndGet();
                    DRAWARRAY_VERTICES.addAndGet(count);
                    celtium$noteAcceptedColor(backend, buffer, count);
                    if (celtium$noteDrawArrayLayout(buffer) || DRAWARRAY_PROBE_LINES.getAndDecrement() > 0) {
                        // One line per distinct buffer, at its first accepted draw: what the batch contains, what the
                        // pointer state said it was, and the matrices the immediate path will draw it with. An accepted
                        // draw whose transform is wrong is a wrong picture, and the two halves have to be told apart
                        // from the outside - "the vertices are not the sky" and "the sky is drawn with the identity"
                        // look the same in the frame and are different findings.
                        StringBuilder vertices = new StringBuilder();
                        for (int i = 0; i < 4 && i < count; i++) {
                            long at = address + (long) i * POSITION_STRIDE;
                            vertices.append(i == 0 ? "" : " ").append('(')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at))).append(',')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at + 4))).append(',')
                                    .append(celtium$round3(MemoryUtil.memGetFloat(at + 8))).append(')');
                        }
                        System.out.println("[Celtium] CELTIUM_DRAWARRAY_LAYOUT first=" + first + " count=" + count
                                + " stride=" + POSITION_STRIDE + " format=" + VERTEX_FORMAT_POSITION + " mode=" + mode
                                + " buffer=" + buffer + " pointerOffset=" + offset
                                + " pointerSize=" + this.vertexPointerSize + " pointerType=0x"
                                + Integer.toHexString(this.vertexPointerType) + " pointerStride="
                                + this.vertexPointerStride + " v0..v3=[" + vertices + "] "
                                + celtium$mirroredMatrices(backend)
                                + " texture2dEnabled=" + this.enables.getOrDefault(Integer.valueOf(GL_TEXTURE_2D), Boolean.FALSE)
                                + " site=[" + celtium$callSite(10) + "]");
                        drawArrayFirst = "mode=" + mode + " first=" + first + " count=" + count + " stride="
                                + POSITION_STRIDE + " format=" + VERTEX_FORMAT_POSITION + " buffer=" + buffer
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
        celtium$noteDrawArrayRefusal(reason);
        celtium$reportDrawArrays();
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
            if (this.enables.getOrDefault(Integer.valueOf(caps[i]), Boolean.FALSE).booleanValue()) {
                if (enabled.length() > 0) {
                    enabled.append(", ");
                }
                enabled.append(names[i]);
            }
        }
        return enabled.length() == 0 ? null : enabled.toString();
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
        return DRAWARRAY_LAYOUTS_LOGGED.size() < 8 && DRAWARRAY_LAYOUTS_LOGGED.add(Integer.valueOf(buffer));
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
    private static String celtium$projectionRefusal(TerrainGpuBackend backend) {
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
            return "the mirrored projection is the identity at draw time (Kirino's gluPerspective does not reach the"
                    + " mirror), so this batch would be drawn with the wrong transform";
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
        if (!this.enables.getOrDefault(Integer.valueOf(GL_FOG), Boolean.FALSE).booleanValue()
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
            frames.append(name).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            if (++taken >= limit) {
                break;
            }
        }
        return frames.toString();
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
            // (texture, pname, out): the filter and wrap state the registry announces from. Defaults are the GL
            // defaults, which is what a texture created without an explicit parameter really has.
            int filterPname = (int) c[1];
            int value = filterPname == 0x2801 || filterPname == 0x2800 ? 0x2601 // GL_TEXTURE_MIN/MAG_FILTER
                    : filterPname == 0x2802 || filterPname == 0x2803 ? 0x812F // GL_TEXTURE_WRAP_S/T
                    : 0;
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
                Integer bound = this.bufferBindings.get(Integer.valueOf((int) c[0]));
                ByteBuffer storage = bound == null ? null : this.bufferStore.get(bound);
                if ("glGetBufferParameteri64v".equals(fn.name)) {
                    memPutLong(out, storage == null ? 0L : storage.capacity());
                } else {
                    memPutInt(out, storage == null ? 0 : storage.capacity());
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
