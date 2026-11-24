package org.soutalab.celtium.vulkan.gl;

/**
 * The stub's function table, extracted from {@code GlsmFrontend} unchanged in behaviour
 * (refactor/giant-split-4): every GL entry point the stub knows, grouped by the shape of its
 * answer - name allocation and queries, buffers and textures, state and draws, and the plain
 * no-ops - is registered here, in the stub's id order, while the stub keeps the table itself,
 * the name-to-id lookup and the dispatch the registered rows feed.
 * <p>
 * Registration order is behaviour: a function's id is its row index, so the four groups run in
 * the order the stub's static block has always run them, and a row is never reordered without
 * renumbering every id the trampolines cached. The rows are built through the stub's
 * {@code fn(...)} factory and name its semantics ids, which is why those ids are package-private
 * (the same shape the argument-type encoding already had for {@link GlTrampolines}).
 */
final class GlFunctionTable {

    /** Registers every row, in id order. Called once from the stub's static block. */
    static void registerAll() {
        registerNamesAndQueries();
        registerBuffersAndTextures();
        registerStateAndDraws();
        registerNoops();
    }

    private static void registerNamesAndQueries() {
        // ---- names: every generator returns a fresh id; the same table's delete frees it for reporting only
        GlsmFrontend.fn("glGenTextures", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenBuffers", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenRenderbuffers", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenFramebuffers", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenVertexArrays", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenSamplers", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenQueries", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenLists", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glCreateShader", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glCreateProgram", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN);
        GlsmFrontend.fn("glGenSync", GlsmFrontend.R_LONG, GlsmFrontend.SEM_GEN);
        GlsmFrontend.fn("glFenceSync", GlsmFrontend.R_LONG, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glGenTransformFeedbacks", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenProgramPipelines", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        // ---- queries that shape what the game thinks it has
        GlsmFrontend.fn("glGetString", GlsmFrontend.R_PTR, GlsmFrontend.SEM_GET_STRING, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glGetStringi", GlsmFrontend.R_PTR, GlsmFrontend.SEM_GET_STRINGI, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glGetError", GlsmFrontend.R_INT, GlsmFrontend.SEM_GET_ERROR);
        GlsmFrontend.fn("glIsEnabled", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_ENABLED, GlsmFrontend.A_INT);   // GLboolean - byte, marshalled as int by the caller
        GlsmFrontend.fn("glIsEnabledi", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_ENABLED, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsTexture", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsBuffer", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsFramebuffer", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsRenderbuffer", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsVertexArray", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsShader", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsProgram", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsQuery", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsSampler", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsList", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsSync", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_LONG);
        GlsmFrontend.fn("glGetIntegerv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GET_INTEGERV, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetInteger64v", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GET_INTEGER64V, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetFloatv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GET_FLOATV, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetDoublev", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GET_FLOATV, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);   // doubles read as floats is a known GL quirk - 4B each
        GlsmFrontend.fn("glGetBooleanv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GET_BOOLEANV, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glEnable", GlsmFrontend.R_VOID, GlsmFrontend.SEM_ENABLE, GlsmFrontend.A_INT);
        // BH-08: the immediate path's cull state never reached the renderer because the face calls were no-ops and
        // nothing read the capability. Both are recorded here instead: the enable already lands in the enables map
        // with every other capability, and the face and winding get their own semantic case.
        GlsmFrontend.fn("glCullFace", GlsmFrontend.R_VOID, GlsmFrontend.SEM_CULL_FACE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glFrontFace", GlsmFrontend.R_VOID, GlsmFrontend.SEM_FRONT_FACE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glPolygonOffset", GlsmFrontend.R_VOID, GlsmFrontend.SEM_POLYGON_OFFSET, GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT);
        GlsmFrontend.fn("glDisable", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DISABLE, GlsmFrontend.A_INT);
        // BH-09: Celeritas' own UI clips with GL11.glScissor directly (VintageDrawContext.enableScissor), so the
        // rectangle must be recorded rather than dropped - it was in the NOOP table, which is why a scroll list could
        // not be clipped. The enable bit is not a second mirror: scissorTest() reads the same enables map that
        // glEnable/glDisable write.
        GlsmFrontend.fn("glScissor", GlsmFrontend.R_VOID, GlsmFrontend.SEM_SCISSOR, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glEnablei", GlsmFrontend.R_VOID, GlsmFrontend.SEM_ENABLE, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glDisablei", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DISABLE, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        // ---- deletion: retire names so glIs* answers go false (names are never reissued - GL does not require it)
        GlsmFrontend.fn("glDeleteTextures", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteBuffers", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteRenderbuffers", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteFramebuffers", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteVertexArrays", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteSamplers", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteQueries", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteLists", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glDeleteShader", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glDeleteProgram", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glDeleteSync", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_LONG);
        // ---- return-valued queries: a 0/garbage return would break callers that read it
        GlsmFrontend.fn("glCheckFramebufferStatus", GlsmFrontend.R_INT, GlsmFrontend.SEM_FBO_COMPLETE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glCheckNamedFramebufferStatus", GlsmFrontend.R_INT, GlsmFrontend.SEM_FBO_COMPLETE, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glClientWaitSync", GlsmFrontend.R_INT, GlsmFrontend.SEM_SIGNALED, GlsmFrontend.A_LONG, GlsmFrontend.A_INT, GlsmFrontend.A_LONG);
        GlsmFrontend.fn("glRenderMode", GlsmFrontend.R_INT, GlsmFrontend.SEM_ZERO, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glGetUniformLocation", GlsmFrontend.R_INT, GlsmFrontend.SEM_ZERO, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetAttribLocation", GlsmFrontend.R_INT, GlsmFrontend.SEM_ZERO, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetUniformBlockIndex", GlsmFrontend.R_INT, GlsmFrontend.SEM_ZERO, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetProgramResourceIndex", GlsmFrontend.R_INT, GlsmFrontend.SEM_ZERO, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateShaderProgramv", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTexLevelParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        // The DSA forms the modern renderer path actually calls: without these every size query answers 0, so
        // announcements are refused as "0x0" and mirrors are built 1x1 before the upload resizes them.
        GlsmFrontend.fn("glGetTextureLevelParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTextureLevelParameterfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTextureParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTextureParameterfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetNamedFramebufferAttachmentParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetFramebufferAttachmentParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTexLevelParameterfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetShaderiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetProgramiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetShaderInfoLog", GlsmFrontend.R_VOID, GlsmFrontend.SEM_INFO_LOG, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetProgramInfoLog", GlsmFrontend.R_VOID, GlsmFrontend.SEM_INFO_LOG, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetBufferParameteri64v", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetBufferParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetSynciv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_LONG, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetFramebufferParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetFramebufferAttachmentParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetRenderbufferParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetVertexAttribiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetVertexAttribPointerv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetUniformfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetUniformiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetActiveUniform", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetActiveAttrib", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetActiveUniformsiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetActiveUniformBlockiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetProgramResourceiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetProgramResourceName", GlsmFrontend.R_VOID, GlsmFrontend.SEM_INFO_LOG, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetShaderSource", GlsmFrontend.R_VOID, GlsmFrontend.SEM_INFO_LOG, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetAttachedShaders", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetPointerv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetBufferPointerv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetQueryObjectiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetQueryObjectuiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetQueryObjecti64v", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetQueryObjectui64v", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
    }

    private static void registerBuffersAndTextures() {
        // ---- buffer storage: glBufferData allocates, glMapBuffer returns the storage address
        GlsmFrontend.fn("glBufferData", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BUFFER_DATA, GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_PTR, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glNamedBufferData", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BUFFER_DATA, GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_PTR, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBufferStorage", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BUFFER_DATA, GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_PTR, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glNamedBufferStorage", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BUFFER_DATA, GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_PTR, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glMapBuffer", GlsmFrontend.R_PTR, GlsmFrontend.SEM_MAP, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glMapNamedBuffer", GlsmFrontend.R_PTR, GlsmFrontend.SEM_MAP, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glMapBufferRange", GlsmFrontend.R_PTR, GlsmFrontend.SEM_MAP, GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_LONG, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glMapNamedBufferRange", GlsmFrontend.R_PTR, GlsmFrontend.SEM_MAP, GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_LONG, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glUnmapBuffer", GlsmFrontend.R_INT, GlsmFrontend.SEM_UNMAP, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glUnmapNamedBuffer", GlsmFrontend.R_INT, GlsmFrontend.SEM_UNMAP, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBindBuffer", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BIND_BUFFER, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        // ---- texture bindings/images: size tracking feeds glGetTexLevelParameter (FML probes the max texture
        // size by uploading proxy images) and the pixel capture the Vulkan mirrors are built from.
        GlsmFrontend.fn("glBindTexture", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BIND_TEXTURE, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBindImageTexture", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BIND_IMAGE_TEXTURE, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glActiveTexture", GlsmFrontend.R_VOID, GlsmFrontend.SEM_ACTIVE_TEXTURE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glTexImage1D", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEXIMAGE_PIXELS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTexImage2D", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEXIMAGE_PIXELS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        // glTexImage3D carries depth between height and border: (target,level,internal,w,h,depth,border,fmt,type,pixels)
        GlsmFrontend.fn("glTexImage3D", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEXIMAGE_PIXELS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTexStorage2D", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEXIMAGE, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glTexStorage3D", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEXIMAGE, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glCompressedTexImage2D", GlsmFrontend.R_VOID, GlsmFrontend.SEM_COMPRESSED_TEXIMAGE, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTexSubImage2D", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEXSUBIMAGE, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glPixelStorei", GlsmFrontend.R_VOID, GlsmFrontend.SEM_PIXEL_STORE, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glViewport", GlsmFrontend.R_VOID, GlsmFrontend.SEM_VIEWPORT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        // Texture-environment state. The stub used to no-op all of it, so the two queries the renderer asks - the
        // combine mode and the environment colour, both on the lightmap unit - returned zero and the hurt flash was
        // invisible to the instrument as well as to the frame (measured by entity-models: the reference shifts the
        // zombie head from (78.9,123.7,87.7) to (117.3,105.0,79.4) and ours moves by at most 1.8).
        GlsmFrontend.fn("glFogi", GlsmFrontend.R_VOID, GlsmFrontend.SEM_FOG_I, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glFogf", GlsmFrontend.R_VOID, GlsmFrontend.SEM_FOG_F, GlsmFrontend.A_INT, GlsmFrontend.A_FLOAT);
        GlsmFrontend.fn("glFogfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_FOG_FV, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glFogiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_FOG_IV, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTexEnvi", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_ENV_I, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glTexEnviv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_ENV_I, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTexEnvf", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_ENV_F, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_FLOAT);
        GlsmFrontend.fn("glTexEnvfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_ENV_FV, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        // LWJGL2's GlStateManager.glTexEnv(int,int,FloatBuffer) arrives under the bare name, which is the fv variant:
        // RenderLivingBase.setBrightness writes the hurt/team env colour through exactly this call, and while it sat
        // in the noop list texEnvColor stayed zero - the combine fired but the mix factor was black at 0.
        GlsmFrontend.fn("glTexEnv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_ENV_FV, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTexEnvi", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GET_TEX_ENV_I, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTexEnviv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GET_TEX_ENV_I, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTexEnvfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GET_TEX_ENV_FV, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        // Texture parameters: the filter and wrap state GL keeps per texture object. These used to be in the no-op
        // list, so every texture answered the same hardcoded value and the real one was gone - measured as the
        // options background sampling CLAMP_TO_EDGE where vanilla tiles it with REPEAT (GuiOptions.drawBackground
        // and Cleanroom's patched GuiSlot.drawContainerBackground both draw it with UVs up to height/32), and the
        // sky faces likewise. Recorded per texture *name* rather than per binding because the queries that need it
        // (the mirror's wrap read, the DSA glGetTextureParameter*) name the texture, not the unit.
        GlsmFrontend.fn("glTexParameteri", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_I, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glTexParameterf", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_I, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_FLOAT);
        GlsmFrontend.fn("glTexParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_IV, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTexParameterfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_IV, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTexParameterIiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_IV, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTexParameterIuiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_IV, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTexParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTexParameterfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTexParameterIiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetTexParameterIuiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        // The DSA forms name the texture directly: the modern mirror path asks with glGetTextureParameteri (LWJGL
        // answers that over glGetTextureParameteriv) and sets with glTextureParameteri.
        GlsmFrontend.fn("glTextureParameteri", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_I_DSA, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glTextureParameterf", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_I_DSA, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_FLOAT);
        GlsmFrontend.fn("glTextureParameteriv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_IV_DSA, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTextureParameterfv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_IV_DSA, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTextureParameterIiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_IV_DSA, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glTextureParameterIuiv", GlsmFrontend.R_VOID, GlsmFrontend.SEM_TEX_PARAM_IV_DSA, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        // Matrix multiplication that never goes through GlStateManager. Forge's item transform is the measured case:
        // ForgeHooksClient.handleCameraTransforms -> multiplyCurrentGlMatrix -> GL11.glMultMatrix(matrixBuf)
        // (ForgeHooksClient.java:425-455), so the item's 30/225-degree rotation reached the stub's no-op list while
        // every GlStateManager-routed matrix call reached the mirror - the mirror kept the GUI ortho as the group's
        // modelview and all six faces of a block projected onto one square (the user's flat slot icon). The same entry
        // point is what the GLU perspective helper uses: org.lwjglx.util.glu.Project.gluPerspective calls
        // GL11.glMultMatrixf at two sites (javap of lwjglxx-1.1.22), so this hook is also the projection channel's
        // missing link - glMatrixMode is recorded here so the census says which stack the multiply landed on.
        GlsmFrontend.fn("glMultMatrixf", GlsmFrontend.R_VOID, GlsmFrontend.SEM_MULT_MATRIX, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glMultMatrixd", GlsmFrontend.R_VOID, GlsmFrontend.SEM_MULT_MATRIX, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glMatrixMode", GlsmFrontend.R_VOID, GlsmFrontend.SEM_MATRIX_MODE, GlsmFrontend.A_INT);
    }

    private static void registerStateAndDraws() {
        // Copy-from-framebuffer. The main menu's panorama is built with these: renderSkybox draws the six 256x256
        // cube faces into the window and copies each into the skybox texture, and the blur passes copy their own
        // targets. Registered rather than left in the no-op list so the request is named and counted: under GL-less
        // there is no GL framebuffer to read the pixels from, so what is missing is a readback of the renderer's own
        // image for the bound read framebuffer, which is a different component's resource.
        GlsmFrontend.fn("glCopyTexImage2D", GlsmFrontend.R_VOID, GlsmFrontend.SEM_COPY_TEX_IMAGE, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glCopyTexSubImage2D", GlsmFrontend.R_VOID, GlsmFrontend.SEM_COPY_TEX_SUB_IMAGE, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        // Texture-to-texture copy: unlike the framebuffer copies above, both levels live in the stub's own
        // capture, so this one is served in full - it is FoamFix's fast animation path (see copyImageSubData).
        GlsmFrontend.fn("glCopyImageSubData", GlsmFrontend.R_VOID, GlsmFrontend.SEM_COPY_IMAGE_SUB_DATA, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT,
                GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBlitFramebuffer", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BLIT_FRAMEBUFFER, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT,
                GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        // glReadPixels is the same question the copies answer, asked into a client buffer: the bound read framebuffer's
        // rectangle from the image the renderer owns. It used to sit in the no-op list, which left every caller's
        // buffer zeroed - the fixture's format-2 readback measured "all mismatch" and ScreenShotHelper's screenshots
        // came out blank - so it is registered and served now.
        GlsmFrontend.fn("glReadPixels", GlsmFrontend.R_VOID, GlsmFrontend.SEM_READ_PIXELS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glBindFramebuffer", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BIND_FRAMEBUFFER, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBindFramebufferEXT", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BIND_FRAMEBUFFER, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBindFramebufferARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BIND_FRAMEBUFFER, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        // ---- clears: with no GL framebuffer to clear, the request is recorded for the owned frame image. Without
        // this the image's depth holds undefined values for the whole session and every depth-tested draw fails.
        GlsmFrontend.fn("glClear", GlsmFrontend.R_VOID, GlsmFrontend.SEM_CLEAR, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glClearColor", GlsmFrontend.R_VOID, GlsmFrontend.SEM_CLEAR_COLOR, GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT);
        GlsmFrontend.fn("glClearDepth", GlsmFrontend.R_VOID, GlsmFrontend.SEM_CLEAR_DEPTH, GlsmFrontend.A_DOUBLE);
        GlsmFrontend.fn("glClearDepthf", GlsmFrontend.R_VOID, GlsmFrontend.SEM_CLEAR_DEPTH, GlsmFrontend.A_FLOAT);
        // ---- state the getters answer from: recorded rather than left at the no-op default
        GlsmFrontend.fn("glLineWidth", GlsmFrontend.R_VOID, GlsmFrontend.SEM_LINE_WIDTH, GlsmFrontend.A_FLOAT);
        GlsmFrontend.fn("glUseProgram", GlsmFrontend.R_VOID, GlsmFrontend.SEM_USE_PROGRAM, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glNewList", GlsmFrontend.R_VOID, GlsmFrontend.SEM_NEW_LIST, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glEndList", GlsmFrontend.R_VOID, GlsmFrontend.SEM_END_LIST);
        GlsmFrontend.fn("glCallList", GlsmFrontend.R_VOID, GlsmFrontend.SEM_CALL_LIST, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glListBase", GlsmFrontend.R_VOID, GlsmFrontend.SEM_LIST_BASE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glCallLists", GlsmFrontend.R_VOID, GlsmFrontend.SEM_CALL_LISTS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glBindVertexArray", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BIND_VERTEX_ARRAY, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBufferSubData", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BUFFER_SUBDATA, GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_LONG, GlsmFrontend.A_PTR);
        // ---- client-array drawing: the pointer state that says how to read the bound array buffer's bytes, and the
        // draw that uses it. This is the VBO path RenderGlobal.renderSky takes (`skyVBO.drawArrays(7)` ->
        // VertexBuffer.drawArrays -> GlStateManager.glDrawArrays -> GL11.glDrawArrays), and it is the one entry point
        // here that produces geometry rather than state: answering it with a no-op is a hole in the picture, not a
        // state detail - measured as a GL-less world frame with no sky dome, no stars and no horizon plane while no
        // counter mentioned them.
        //
        // glEnableClientState/glDisableClientState are the same capability bits glEnable sets (GL_VERTEX_ARRAY is
        // 32884), so they share those semantics: glIsEnabled answers from the same record, as GL does.
        GlsmFrontend.fn("glEnableClientState", GlsmFrontend.R_VOID, GlsmFrontend.SEM_ENABLE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glDisableClientState", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DISABLE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glVertexPointer", GlsmFrontend.R_VOID, GlsmFrontend.SEM_VERTEX_POINTER, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDrawArrays", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DRAW_ARRAYS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBegin", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BEGIN, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glEnd", GlsmFrontend.R_VOID, GlsmFrontend.SEM_END);
        // ---- the blend and colour-write state (BH-07, BH-10): the two the games' own wrappers reach through
        // OpenGlHelper rather than through a GlStateManager field, which is why a mixin hook could not see them.
        // Read from the original: `GlStateManager.blendFunc(int,int)` calls `GL11.glBlendFunc` directly, while both
        // `tryBlendFuncSeparate` overloads delegate to `OpenGlHelper.glBlendFunc`, which with OpenGL14 present (the
        // stub reports GL 4.6, so it is) calls `GL14.glBlendFuncSeparate`. The mixin hooks the enum overload only, so
        // the int overload - the one JsonBlendingMode and every Forge caller with plain ints uses - reached the
        // no-op table and the mirror kept the last enum call's factors. Recording here catches every caller of every
        // shape, including raw GL, for the same reason the cull capability is recorded here (BH-08).
        GlsmFrontend.fn("glBlendFunc", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BLEND_FUNC, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBlendFuncSeparate", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BLEND_FUNC_SEPARATE, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBlendEquation", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BLEND_EQUATION, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBlendEquationSeparate", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BLEND_EQUATION_SEPARATE, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        // BH-10: the Fancy-cloud depth-only pass reaches this through GlStateManager.colorMask, which is itself
        // cached and calls GL11.glColorMask only when the mask changes - so the stub is the one place that sees the
        // change that matters (false,false,false,false at RenderGlobal:1655).
        GlsmFrontend.fn("glColorMask", GlsmFrontend.R_VOID, GlsmFrontend.SEM_COLOR_MASK, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        // ---- DSA create-functions: same out-array shape as the glGen* family
        GlsmFrontend.fn("glCreateTextures", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateBuffers", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateFramebuffers", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateRenderbuffers", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateVertexArrays", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateSamplers", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateQueries", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateTransformFeedbacks", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateProgramPipelines", GlsmFrontend.R_VOID, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        // ---- ARB/EXT-suffixed entry points (roam-vanilla-diff audit, 2026-11-22): LWJGL2 exposes the
        // framebuffer, buffer-object, shader-object, query, multitexture and copy-image families under
        // ARB/EXT-suffixed names as separate native symbols. Only glBindFramebufferARB/EXT were registered, so a
        // caller on the ARB/EXT dispatch path (OpenGlHelper picks it by reported capability; some mods call the
        // suffixed names directly) could bind a framebuffer but not create, check or delete one - the gen calls
        // fell into the unknown-symbol record and returned 0, which a caller reads as the default framebuffer.
        // That is the same silent-wrongness shape as the once-unrouted glCopyImageSubData. The suffixed names are
        // semantically identical to their core names in LWJGL2, so they alias to the same semantics and argument
        // shapes; suffixed names whose core form is a deliberate no-op are listed with the no-ops below instead,
        // so the unknown-symbol census stops charging them.
        GlsmFrontend.fn("glGenFramebuffersEXT", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenRenderbuffersEXT", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteFramebuffersEXT", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteRenderbuffersEXT", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glIsFramebufferEXT", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsRenderbufferEXT", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glCheckFramebufferStatusEXT", GlsmFrontend.R_INT, GlsmFrontend.SEM_FBO_COMPLETE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glGenFramebuffersARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGenRenderbuffersARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteFramebuffersARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteRenderbuffersARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glIsFramebufferARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glIsRenderbufferARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glCheckFramebufferStatusARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_FBO_COMPLETE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glGenBuffersARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteBuffersARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glIsBufferARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBindBufferARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BIND_BUFFER, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBufferDataARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BUFFER_DATA, GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_PTR, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glBufferSubDataARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_BUFFER_SUBDATA, GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_LONG, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glMapBufferARB", GlsmFrontend.R_PTR, GlsmFrontend.SEM_MAP, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glUnmapBufferARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_UNMAP, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glGetBufferParameterivARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCreateShaderObjectARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glCreateProgramObjectARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN);
        GlsmFrontend.fn("glDeleteObjectARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glUseProgramObjectARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_USE_PROGRAM, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glGetUniformLocationARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_ZERO, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetObjectParameteriARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetInfoLogARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_INFO_LOG, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetActiveUniformARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetAttachedObjectsARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_STATUS, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glGetShaderSourceARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_INFO_LOG, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glCopyImageSubDataARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_COPY_IMAGE_SUB_DATA, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT,
                GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glActiveTextureARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_ACTIVE_TEXTURE, GlsmFrontend.A_INT);
        GlsmFrontend.fn("glGenQueriesARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_GEN, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glDeleteQueriesARB", GlsmFrontend.R_VOID, GlsmFrontend.SEM_DELETE, GlsmFrontend.A_INT, GlsmFrontend.A_PTR);
        GlsmFrontend.fn("glIsQueryARB", GlsmFrontend.R_INT, GlsmFrontend.SEM_IS_OBJECT, GlsmFrontend.A_INT);
    }

    private static void registerNoops() {
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
                "glShadeModel", "glTexGeni", "glTexGenf", "glTexGeniv", "glTexGenfv", "glAlphaFunc", "glNormal3f",
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
                "glEndQueryIndexed", "glWaitSync", "glFlush", "glFinish", "glPushAttrib",
                "glPopAttrib", "glPushClientAttrib", "glPopClientAttrib", "glSelectBuffer", "glFeedbackBuffer", "glInitNames",
                "glPushName", "glPopName", "glLoadName", "glRasterPos2f", "glRasterPos3f", "glBitmap",
                "glCopyPixels", "glDrawPixels",
                "glFramebufferTexture2D", "glFramebufferTexture", "glFramebufferTexture1D",
                "glFramebufferTexture3D", "glFramebufferTextureLayer", "glFramebufferRenderbuffer",
                "glRenderbufferStorage", "glRenderbufferStorageMultisample",
                "glTexSubImage1D", "glTexSubImage3D",
                "glCompressedTexSubImage2D", "glGetTexImage",
                "glGetCompressedTexImage", "glCopyBufferSubData", "glFlushMappedBufferRange", "glGetBufferSubData",
                "glTexCoordPointer", "glColorPointer", "glNormalPointer", "glIndexPointer", "glEdgeFlagPointer",
                "glSecondaryColorPointer", "glFogCoordPointer", "glInterleavedArrays", "glArrayElement", "glRects", "glClipPlane", "glSecondaryColor3fv", "glColor4fv",
                "glDepthBoundsEXT", "glMultiDrawArraysIndirect", "glMultiDrawElementsIndirect", "glDrawArraysIndirect",
                "glDrawElementsIndirect", "glDispatchCompute", "glMemoryBarrier", "glTextureBarrier",
                "glFramebufferParameteri", "glNamedFramebufferTexture",
                "glNamedFramebufferRenderbuffer", "glNamedRenderbufferStorage",
                "glTextureImage2DEXT",
                "glTextureSubImage2D", "glNamedTexImage2DEXT", "glNamedCopyTexSubImage2DEXT", "glTextureStorage2D",
                "glGenerateTextureMipmap", "glBindTextureUnit",
                "glTexBuffer", "glTexBufferRange",
                "glUniformBlockBinding",
                "glUseProgramStages", "glActiveShaderProgram", "glProgramUniform1i", "glProgramUniform1f",
                "glProgramUniform2f", "glProgramUniform3f", "glProgramUniform4f", "glProgramUniform1iv",
                "glProgramUniformMatrix4fv",
                // ARB/EXT-suffixed forms of the no-op rows above (roam-vanilla-diff audit, 2026-11-22): same
                // deliberate no-op as their core names, registered so the unknown-symbol census stops charging
                // them and an ARB/EXT-path caller sees the same surface as a core-path caller.
                "glBindRenderbufferEXT", "glBindRenderbufferARB",
                "glFramebufferTexture2DEXT", "glFramebufferTexture2DARB",
                "glFramebufferRenderbufferEXT", "glFramebufferRenderbufferARB",
                "glRenderbufferStorageEXT", "glRenderbufferStorageARB",
                "glGenerateMipmapEXT", "glGenerateMipmapARB",
                "glShaderSourceARB", "glCompileShaderARB", "glAttachObjectARB", "glDetachObjectARB",
                "glLinkProgramARB", "glValidateProgramARB",
                "glUniform1iARB", "glUniform1fARB", "glUniform2fARB", "glUniform3fARB", "glUniform4fARB",
                "glUniform1ivARB", "glUniform2ivARB", "glUniform3ivARB", "glUniform4ivARB",
                "glUniform1fvARB", "glUniform2fvARB", "glUniform3fvARB", "glUniform4fvARB",
                "glUniformMatrix2fvARB", "glUniformMatrix3fvARB", "glUniformMatrix4fvARB",
                "glVertexAttribPointerARB", "glEnableVertexAttribArrayARB", "glDisableVertexAttribArrayARB",
                "glBeginQueryARB", "glEndQueryARB",
                "glMultiTexCoord2fARB"
        };
        for (String name : noops) {
            // Rough signature for the table: every arg marshals as int/pointer uniformly - the noop discards them,
            // so an arg count is all the trampoline needs.
            GlsmFrontend.fn(name, GlsmFrontend.R_VOID, GlsmFrontend.SEM_NOOP, new byte[] { GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR, GlsmFrontend.A_PTR });
        }
    }
}
