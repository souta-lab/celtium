package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *   <li>Everything else records and no-ops - drawing, blending and state setting the Vulkan side already mirrors
 *   or owns, so dropping them loses nothing the frame needed.</li>
 * </ul>
 * The pixel-carrying functions ({@code glTexImage2D} and friends) are the next layer: they are table entries whose
 * semantics id marks "route the upload" - the handler lands with the texture-path work, one function at a time.
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
        // size by uploading proxy images) and, later, the pixel-routing layer.
        fn("glBindTexture", R_VOID, SEM_BIND_TEXTURE, A_INT, A_INT);
        fn("glActiveTexture", R_VOID, SEM_ACTIVE_TEXTURE, A_INT);
        fn("glTexImage1D", R_VOID, SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        fn("glTexImage2D", R_VOID, SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        fn("glTexImage3D", R_VOID, SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        fn("glTexStorage2D", R_VOID, SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT);
        fn("glTexStorage3D", R_VOID, SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        fn("glCompressedTexImage2D", R_VOID, SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
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
                "glBindFramebuffer", "glBindRenderbuffer",
                "glBindVertexArray", "glBindSampler", "glBindBufferBase", "glBindBufferRange", "glBlendFunc",
                "glBlendFuncSeparate", "glBlendFuncSeparatei", "glBlendEquation", "glBlendEquationSeparate",
                "glBlendColor", "glColorMask", "glColorMaski", "glDepthMask", "glDepthFunc", "glDepthRange",
                "glClearColor", "glClearDepth", "glClearStencil", "glClear", "glClearBufferfv", "glClearBufferiv",
                "glClearBufferuiv", "glStencilFunc", "glStencilFuncSeparate", "glStencilOp", "glStencilOpSeparate",
                "glStencilMask", "glStencilMaskSeparate", "glCullFace", "glFrontFace", "glPolygonMode",
                "glPolygonOffset", "glLineWidth", "glPointSize", "glPrimitiveRestartIndex", "glProvokingVertex",
                "glSampleCoverage", "glSampleMaski", "glScissor", "glViewport", "glLogicOp", "glHint",
                "glPixelStorei", "glPixelStoref", "glReadBuffer", "glDrawBuffer", "glDrawBuffers", "glClampColor",
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
                "glUseProgram", "glAttachShader", "glDetachShader", "glBindAttribLocation", "glBindFragDataLocation",
                "glLinkProgram", "glValidateProgram", "glShaderSource", "glCompileShader", "glUniform1i",
                "glUniform1f", "glUniform2f", "glUniform3f", "glUniform4f", "glUniform1iv", "glUniform2iv",
                "glUniform3iv", "glUniform4iv", "glUniform1fv", "glUniform2fv", "glUniform3fv", "glUniform4fv",
                "glUniformMatrix2fv", "glUniformMatrix3fv", "glUniformMatrix4fv", "glEnableVertexAttribArray",
                "glDisableVertexAttribArray", "glVertexAttribPointer", "glVertexAttribIPointer",
                "glVertexAttribLPointer", "glVertexAttribDivisor", "glVertexAttrib1f", "glVertexAttrib2f",
                "glVertexAttrib3f", "glVertexAttrib4f", "glVertexAttrib4Nub", "glDrawArrays", "glDrawElements",
                "glDrawRangeElements", "glDrawElementsInstanced", "glDrawArraysInstanced", "glMultiDrawArrays",
                "glMultiDrawElements", "glDrawElementsBaseVertex", "glBeginQuery", "glEndQuery", "glBeginQueryIndexed",
                "glEndQueryIndexed", "glClientWaitSync", "glWaitSync", "glFlush", "glFinish", "glPushAttrib",
                "glPopAttrib", "glPushClientAttrib", "glPopClientAttrib", "glCallList", "glCallLists", "glNewList",
                "glEndList", "glListBase", "glRenderMode", "glSelectBuffer", "glFeedbackBuffer", "glInitNames",
                "glPushName", "glPopName", "glLoadName", "glRasterPos2f", "glRasterPos3f", "glBitmap",
                "glCopyPixels", "glDrawPixels", "glReadPixels", "glCopyTexImage2D", "glCopyTexSubImage2D",
                "glBlitFramebuffer", "glFramebufferTexture2D", "glFramebufferTexture", "glFramebufferTexture1D",
                "glFramebufferTexture3D", "glFramebufferTextureLayer", "glFramebufferRenderbuffer",
                "glRenderbufferStorage", "glRenderbufferStorageMultisample",
                "glTexSubImage1D", "glTexSubImage2D", "glTexSubImage3D",
                "glCompressedTexSubImage2D", "glGetTexImage",
                "glGetCompressedTexImage", "glBufferData", "glBufferSubData", "glMapBuffer", "glMapBufferRange",
                "glUnmapBuffer", "glCopyBufferSubData", "glFlushMappedBufferRange", "glGetBufferSubData",
                "glEnableClientState", "glDisableClientState", "glVertexPointer",
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

    private final Map<String, CeltiumGlTrampoline> trampolines = new HashMap<>();
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
    private final java.util.Map<Integer, ByteBuffer> bufferStore = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, Integer> lastBoundOnTarget = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<CeltiumGlTrampoline> ownedTrampolines = new ArrayList<>();
    private long getStringAddress;
    private ByteBuffer getStringBuffer;
    private volatile int viewportW = 1280, viewportH = 720;

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
        return this.trampolines.computeIfAbsent(name, n -> {
            CeltiumGlTrampoline trampoline = new CeltiumGlTrampoline(id, this);
            this.ownedTrampolines.add(trampoline);
            return trampoline;
        }).address();
    }

    // ---- trampoline plumbing ---------------------------------------------------------

    /**
     * One uniform call interface covers every function in the table: the FFM upcall invokes the SAM with the C
     * arguments marshalled per the CIF, so a CIF of eight pointer-sized arguments means {@code invoke} receives up
     * to eight argument slots. Callers of the real functions supply fewer - the extra slots carry garbage that the
     * dispatcher ignores; callers expecting narrower results (int or nothing) read only the low bits or discard the
     * value entirely. No function in the table exceeds eight arguments.
     */
    private static final Callback.Descriptor UNIFORM_DESCRIPTOR = new Callback.Descriptor(
            java.lang.invoke.MethodHandles.lookup(),
            apiCreateCIF(ffi_type_pointer, new FFIType[] {
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer,
                    ffi_type_pointer, ffi_type_pointer, ffi_type_pointer, ffi_type_pointer }));

    /**
     * The upcall interface the FFM backend binds: one abstract method receiving the call's argument values. The
     * legacy {@code callback} entry is not used by the FFM path and stays a stub.
     */
    @FunctionalInterface
    public interface GlTrampolineI extends org.lwjgl.system.CallbackI {
        long invoke(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7);

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
     * One native trampoline per function name. Two call entries exist because which backend LWJGL uses is a
     * runtime property, not a compile-time one: {@code invoke} serves the FFM backend, {@code callback} the
     * legacy libffi one.
     */
    private static final class CeltiumGlTrampoline extends Callback implements GlTrampolineI {
        private final int functionId;
        private final CeltiumGlStub stub;

        CeltiumGlTrampoline(int functionId, CeltiumGlStub stub) {
            super(UNIFORM_DESCRIPTOR);
            this.functionId = functionId;
            this.stub = stub;
        }

        /** The FFM backend's entry: the C call's arguments arrive as this SAM's parameters, in order. */
        @Override
        public long invoke(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7) {
            return this.stub.dispatch(this.functionId, a0, a1, a2, a3, a4, a5, a6, a7);
        }

        /**
         * The legacy (libffi) backend's entry: {@code args} is an array of pointers, one per C argument, each
         * pointing at that argument's storage; the result goes to the closure-owned slot {@code result}.
         */
        @Override
        public void callback(long result, long args) {
            this.stub.legacyDispatch(this.functionId, result, args);
        }
    }

    // ---- dispatch ---------------------------------------------------------------------

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
        long[] c = new long[8];
        for (int i = 0; i < fn.args.length && i < 8; i++) {
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
        APIUtil.apiClosureRetP(result, dispatch(functionId, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]));
    }

    /**
     * Handles a call. {@code c} is the call's argument values in order - ints widened to longs, pointers as raw
     * addresses. Only the first {@code fn.args.length} slots are meaningful. The return value goes into the
     * caller's result register; narrower results use the low bits.
     */
    private long dispatch(int functionId, long c0, long c1, long c2, long c3, long c4, long c5, long c6, long c7) {
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
        long[] c = { c0, c1, c2, c3, c4, c5, c6, c7 };
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
                this.activeTexture = (int) a0;
                return 0L;
            case SEM_TEXIMAGE:
                texImage(fn, c);
                return 0L;
            case SEM_ROUTE:
                route(fn, c);
                return 0L;
            case SEM_NOOP:
            default:
                return 0L;
        }
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
        if (fn.args.length == 2 && fn.args[0] == A_INT && fn.args[1] == A_PTR) {
            int count = (int) c[0];
            long names = c[1];
            if (names == 0L) {
                return;
            }
            for (int i = 0; i < count; i++) {
                this.liveNames.remove(Integer.valueOf(memGetInt(names + (long) i * 4L)));
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
            case 0x8069: // GL_TEXTURE_BINDING_2D
            case 0x84E0: // near GL_ACTIVE_TEXTURE - wrong on purpose check: ACTIVE_TEXTURE is 0x84E0, writes the
                // selector; binding queries get 0 = no texture bound.
            case 0x84C0: // GL_TEXTURE0 read back as an enum answer for ACTIVE_TEXTURE
                value = 0x84C0;
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

    private void getFloats(int pname, long out, Fn fn) {
        if (out == 0L) {
            return;
        }
        float value;
        switch (pname) {
            case 0x0D3A: // GL_POINT_SIZE_RANGE-ish defaults; matrix queries answer 0 like an unset matrix
            case 0x0B01: // GL_ACCUM_CLEAR_VALUE etc - 0/1 style defaults: pick the honest single value
            default:
                value = 0.0f;
        }
        MemoryUtil.memPutFloat(out, value);
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

    private static boolean isProxyTarget(int target) {
        // GL_PROXY_TEXTURE_1D/2D/3D/CUBE_MAP - uploads to these carry no pixels and bind to nothing.
        return target == 0x8063 || target == 0x8064 || target == 0x8070 || target == 0x851B;
    }

    private long texBindKey(int target) {
        return ((long) this.activeTexture << 16) | (target & 0xFFFFL);
    }

    /**
     * Records the uploaded extent under the texture the target is bound to (or the target itself for proxy
     * uploads). {@code glTexStorage} counts as a level-0 image; {@code glTexImage1D} has no height slot.
     */
    private void texImage(Fn fn, long[] c) {
        int target = (int) c[0];
        int level = "glTexStorage2D".equals(fn.name) || "glTexStorage3D".equals(fn.name) ? 0 : (int) c[1];
        int internal = (int) c[2];
        int w = (int) c[3];
        int h = "glTexImage1D".equals(fn.name) ? 1 : (int) c[4];
        int name;
        if (isProxyTarget(target)) {
            name = -target;
        } else {
            Integer bound = this.textureBindings.get(Long.valueOf(texBindKey(target)));
            name = bound == null ? 0 : bound.intValue();
        }
        if (name == 0) {
            return;
        }
        this.texLevels.put(Long.valueOf(((long) name << 16) | (level & 0xFFL)), new int[] { w, h, internal });
    }

    /** The pname's recorded value for the bound texture's level, or 0 when nothing was uploaded. */
    private int texLevelParam(int target, int level, int pname) {
        int name;
        if (isProxyTarget(target)) {
            name = -target;
        } else {
            Integer bound = this.textureBindings.get(Long.valueOf(texBindKey(target)));
            name = bound == null ? 0 : bound.intValue();
        }
        int[] dims = this.texLevels.get(Long.valueOf(((long) name << 16) | (level & 0xFFL)));
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
        ByteBuffer storage = ByteBuffer.allocateDirect((int) size);
        if (data != 0L) {
            ByteBuffer source = MemoryUtil.memByteBuffer(data, (int) size);
            storage.put(source);
            storage.clear();
        }
        this.bufferStore.put(Integer.valueOf(name), storage);
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
