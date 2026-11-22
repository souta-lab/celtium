package org.soutalab.celtium.vulkan.gl;

/**
 * The stub's function table, extracted from {@code CeltiumGlStub} unchanged in behaviour
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
        CeltiumGlStub.fn("glGenTextures", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenBuffers", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenRenderbuffers", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenFramebuffers", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenVertexArrays", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenSamplers", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenQueries", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenLists", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glCreateShader", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glCreateProgram", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN);
        CeltiumGlStub.fn("glGenSync", CeltiumGlStub.R_LONG, CeltiumGlStub.SEM_GEN);
        CeltiumGlStub.fn("glFenceSync", CeltiumGlStub.R_LONG, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glGenTransformFeedbacks", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenProgramPipelines", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        // ---- queries that shape what the game thinks it has
        CeltiumGlStub.fn("glGetString", CeltiumGlStub.R_PTR, CeltiumGlStub.SEM_GET_STRING, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glGetStringi", CeltiumGlStub.R_PTR, CeltiumGlStub.SEM_GET_STRINGI, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glGetError", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GET_ERROR);
        CeltiumGlStub.fn("glIsEnabled", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_ENABLED, CeltiumGlStub.A_INT);   // GLboolean - byte, marshalled as int by the caller
        CeltiumGlStub.fn("glIsEnabledi", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_ENABLED, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsTexture", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsBuffer", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsFramebuffer", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsRenderbuffer", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsVertexArray", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsShader", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsProgram", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsQuery", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsSampler", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsList", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsSync", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_LONG);
        CeltiumGlStub.fn("glGetIntegerv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GET_INTEGERV, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetInteger64v", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GET_INTEGER64V, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetFloatv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GET_FLOATV, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetDoublev", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GET_FLOATV, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);   // doubles read as floats is a known GL quirk - 4B each
        CeltiumGlStub.fn("glGetBooleanv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GET_BOOLEANV, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glEnable", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_ENABLE, CeltiumGlStub.A_INT);
        // BH-08: the immediate path's cull state never reached the renderer because the face calls were no-ops and
        // nothing read the capability. Both are recorded here instead: the enable already lands in the enables map
        // with every other capability, and the face and winding get their own semantic case.
        CeltiumGlStub.fn("glCullFace", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_CULL_FACE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glFrontFace", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_FRONT_FACE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glPolygonOffset", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_POLYGON_OFFSET, CeltiumGlStub.A_FLOAT, CeltiumGlStub.A_FLOAT);
        CeltiumGlStub.fn("glDisable", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DISABLE, CeltiumGlStub.A_INT);
        // BH-09: Celeritas' own UI clips with GL11.glScissor directly (VintageDrawContext.enableScissor), so the
        // rectangle must be recorded rather than dropped - it was in the NOOP table, which is why a scroll list could
        // not be clipped. The enable bit is not a second mirror: scissorTest() reads the same enables map that
        // glEnable/glDisable write.
        CeltiumGlStub.fn("glScissor", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_SCISSOR, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glEnablei", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_ENABLE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glDisablei", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DISABLE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        // ---- deletion: retire names so glIs* answers go false (names are never reissued - GL does not require it)
        CeltiumGlStub.fn("glDeleteTextures", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteBuffers", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteRenderbuffers", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteFramebuffers", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteVertexArrays", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteSamplers", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteQueries", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteLists", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glDeleteShader", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glDeleteProgram", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glDeleteSync", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_LONG);
        // ---- return-valued queries: a 0/garbage return would break callers that read it
        CeltiumGlStub.fn("glCheckFramebufferStatus", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_FBO_COMPLETE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glCheckNamedFramebufferStatus", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_FBO_COMPLETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glClientWaitSync", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_SIGNALED, CeltiumGlStub.A_LONG, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG);
        CeltiumGlStub.fn("glRenderMode", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_ZERO, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glGetUniformLocation", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_ZERO, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetAttribLocation", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_ZERO, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetUniformBlockIndex", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_ZERO, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetProgramResourceIndex", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_ZERO, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateShaderProgramv", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTexLevelParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        // The DSA forms the modern renderer path actually calls: without these every size query answers 0, so
        // announcements are refused as "0x0" and mirrors are built 1x1 before the upload resizes them.
        CeltiumGlStub.fn("glGetTextureLevelParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTextureLevelParameterfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTextureParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTextureParameterfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetNamedFramebufferAttachmentParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetFramebufferAttachmentParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTexLevelParameterfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetShaderiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetProgramiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetShaderInfoLog", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_INFO_LOG, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetProgramInfoLog", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_INFO_LOG, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetBufferParameteri64v", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetBufferParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetSynciv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_LONG, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetFramebufferParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetFramebufferAttachmentParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetRenderbufferParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetVertexAttribiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetVertexAttribPointerv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetUniformfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetUniformiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetActiveUniform", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetActiveAttrib", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetActiveUniformsiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetActiveUniformBlockiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetProgramResourceiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetProgramResourceName", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_INFO_LOG, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetShaderSource", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_INFO_LOG, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetAttachedShaders", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetPointerv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetBufferPointerv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetQueryObjectiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetQueryObjectuiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetQueryObjecti64v", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetQueryObjectui64v", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
    }

    private static void registerBuffersAndTextures() {
        // ---- buffer storage: glBufferData allocates, glMapBuffer returns the storage address
        CeltiumGlStub.fn("glBufferData", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BUFFER_DATA, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG, CeltiumGlStub.A_PTR, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glNamedBufferData", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BUFFER_DATA, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG, CeltiumGlStub.A_PTR, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBufferStorage", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BUFFER_DATA, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG, CeltiumGlStub.A_PTR, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glNamedBufferStorage", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BUFFER_DATA, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG, CeltiumGlStub.A_PTR, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glMapBuffer", CeltiumGlStub.R_PTR, CeltiumGlStub.SEM_MAP, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glMapNamedBuffer", CeltiumGlStub.R_PTR, CeltiumGlStub.SEM_MAP, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glMapBufferRange", CeltiumGlStub.R_PTR, CeltiumGlStub.SEM_MAP, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG, CeltiumGlStub.A_LONG, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glMapNamedBufferRange", CeltiumGlStub.R_PTR, CeltiumGlStub.SEM_MAP, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG, CeltiumGlStub.A_LONG, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glUnmapBuffer", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_UNMAP, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glUnmapNamedBuffer", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_UNMAP, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBindBuffer", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BIND_BUFFER, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        // ---- texture bindings/images: size tracking feeds glGetTexLevelParameter (FML probes the max texture
        // size by uploading proxy images) and the pixel capture the Vulkan mirrors are built from.
        CeltiumGlStub.fn("glBindTexture", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BIND_TEXTURE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBindImageTexture", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BIND_IMAGE_TEXTURE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glActiveTexture", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_ACTIVE_TEXTURE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glTexImage1D", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEXIMAGE_PIXELS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTexImage2D", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEXIMAGE_PIXELS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        // glTexImage3D carries depth between height and border: (target,level,internal,w,h,depth,border,fmt,type,pixels)
        CeltiumGlStub.fn("glTexImage3D", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEXIMAGE_PIXELS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTexStorage2D", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEXIMAGE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glTexStorage3D", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEXIMAGE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glCompressedTexImage2D", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_COMPRESSED_TEXIMAGE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTexSubImage2D", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEXSUBIMAGE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glPixelStorei", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_PIXEL_STORE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glViewport", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_VIEWPORT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        // Texture-environment state. The stub used to no-op all of it, so the two queries the renderer asks - the
        // combine mode and the environment colour, both on the lightmap unit - returned zero and the hurt flash was
        // invisible to the instrument as well as to the frame (measured by entity-models: the reference shifts the
        // zombie head from (78.9,123.7,87.7) to (117.3,105.0,79.4) and ours moves by at most 1.8).
        CeltiumGlStub.fn("glFogi", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_FOG_I, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glFogf", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_FOG_F, CeltiumGlStub.A_INT, CeltiumGlStub.A_FLOAT);
        CeltiumGlStub.fn("glFogfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_FOG_FV, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glFogiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_FOG_IV, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTexEnvi", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_ENV_I, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glTexEnviv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_ENV_I, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTexEnvf", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_ENV_F, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_FLOAT);
        CeltiumGlStub.fn("glTexEnvfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_ENV_FV, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        // LWJGL2's GlStateManager.glTexEnv(int,int,FloatBuffer) arrives under the bare name, which is the fv variant:
        // RenderLivingBase.setBrightness writes the hurt/team env colour through exactly this call, and while it sat
        // in the noop list texEnvColor stayed zero - the combine fired but the mix factor was black at 0.
        CeltiumGlStub.fn("glTexEnv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_ENV_FV, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTexEnvi", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GET_TEX_ENV_I, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTexEnviv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GET_TEX_ENV_I, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTexEnvfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GET_TEX_ENV_FV, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        // Texture parameters: the filter and wrap state GL keeps per texture object. These used to be in the no-op
        // list, so every texture answered the same hardcoded value and the real one was gone - measured as the
        // options background sampling CLAMP_TO_EDGE where vanilla tiles it with REPEAT (GuiOptions.drawBackground
        // and Cleanroom's patched GuiSlot.drawContainerBackground both draw it with UVs up to height/32), and the
        // sky faces likewise. Recorded per texture *name* rather than per binding because the queries that need it
        // (the mirror's wrap read, the DSA glGetTextureParameter*) name the texture, not the unit.
        CeltiumGlStub.fn("glTexParameteri", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_I, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glTexParameterf", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_I, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_FLOAT);
        CeltiumGlStub.fn("glTexParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_IV, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTexParameterfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_IV, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTexParameterIiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_IV, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTexParameterIuiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_IV, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTexParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTexParameterfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTexParameterIiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetTexParameterIuiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        // The DSA forms name the texture directly: the modern mirror path asks with glGetTextureParameteri (LWJGL
        // answers that over glGetTextureParameteriv) and sets with glTextureParameteri.
        CeltiumGlStub.fn("glTextureParameteri", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_I_DSA, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glTextureParameterf", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_I_DSA, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_FLOAT);
        CeltiumGlStub.fn("glTextureParameteriv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_IV_DSA, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTextureParameterfv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_IV_DSA, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTextureParameterIiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_IV_DSA, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glTextureParameterIuiv", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_TEX_PARAM_IV_DSA, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        // Matrix multiplication that never goes through GlStateManager. Forge's item transform is the measured case:
        // ForgeHooksClient.handleCameraTransforms -> multiplyCurrentGlMatrix -> GL11.glMultMatrix(matrixBuf)
        // (ForgeHooksClient.java:425-455), so the item's 30/225-degree rotation reached the stub's no-op list while
        // every GlStateManager-routed matrix call reached the mirror - the mirror kept the GUI ortho as the group's
        // modelview and all six faces of a block projected onto one square (the user's flat slot icon). The same entry
        // point is what the GLU perspective helper uses: org.lwjglx.util.glu.Project.gluPerspective calls
        // GL11.glMultMatrixf at two sites (javap of lwjglxx-1.1.22), so this hook is also the projection channel's
        // missing link - glMatrixMode is recorded here so the census says which stack the multiply landed on.
        CeltiumGlStub.fn("glMultMatrixf", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_MULT_MATRIX, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glMultMatrixd", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_MULT_MATRIX, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glMatrixMode", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_MATRIX_MODE, CeltiumGlStub.A_INT);
    }

    private static void registerStateAndDraws() {
        // Copy-from-framebuffer. The main menu's panorama is built with these: renderSkybox draws the six 256x256
        // cube faces into the window and copies each into the skybox texture, and the blur passes copy their own
        // targets. Registered rather than left in the no-op list so the request is named and counted: under GL-less
        // there is no GL framebuffer to read the pixels from, so what is missing is a readback of the renderer's own
        // image for the bound read framebuffer, which is a different component's resource.
        CeltiumGlStub.fn("glCopyTexImage2D", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_COPY_TEX_IMAGE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glCopyTexSubImage2D", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_COPY_TEX_SUB_IMAGE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        // Texture-to-texture copy: unlike the framebuffer copies above, both levels live in the stub's own
        // capture, so this one is served in full - it is FoamFix's fast animation path (see copyImageSubData).
        CeltiumGlStub.fn("glCopyImageSubData", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_COPY_IMAGE_SUB_DATA, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT,
                CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBlitFramebuffer", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BLIT_FRAMEBUFFER, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT,
                CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        // glReadPixels is the same question the copies answer, asked into a client buffer: the bound read framebuffer's
        // rectangle from the image the renderer owns. It used to sit in the no-op list, which left every caller's
        // buffer zeroed - the fixture's format-2 readback measured "all mismatch" and ScreenShotHelper's screenshots
        // came out blank - so it is registered and served now.
        CeltiumGlStub.fn("glReadPixels", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_READ_PIXELS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glBindFramebuffer", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BIND_FRAMEBUFFER, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBindFramebufferEXT", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BIND_FRAMEBUFFER, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBindFramebufferARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BIND_FRAMEBUFFER, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        // ---- clears: with no GL framebuffer to clear, the request is recorded for the owned frame image. Without
        // this the image's depth holds undefined values for the whole session and every depth-tested draw fails.
        CeltiumGlStub.fn("glClear", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_CLEAR, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glClearColor", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_CLEAR_COLOR, CeltiumGlStub.A_FLOAT, CeltiumGlStub.A_FLOAT, CeltiumGlStub.A_FLOAT, CeltiumGlStub.A_FLOAT);
        CeltiumGlStub.fn("glClearDepth", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_CLEAR_DEPTH, CeltiumGlStub.A_DOUBLE);
        CeltiumGlStub.fn("glClearDepthf", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_CLEAR_DEPTH, CeltiumGlStub.A_FLOAT);
        // ---- state the getters answer from: recorded rather than left at the no-op default
        CeltiumGlStub.fn("glLineWidth", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_LINE_WIDTH, CeltiumGlStub.A_FLOAT);
        CeltiumGlStub.fn("glUseProgram", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_USE_PROGRAM, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glNewList", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_NEW_LIST, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glEndList", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_END_LIST);
        CeltiumGlStub.fn("glCallList", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_CALL_LIST, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glListBase", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_LIST_BASE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glCallLists", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_CALL_LISTS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glBindVertexArray", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BIND_VERTEX_ARRAY, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBufferSubData", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BUFFER_SUBDATA, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG, CeltiumGlStub.A_LONG, CeltiumGlStub.A_PTR);
        // ---- client-array drawing: the pointer state that says how to read the bound array buffer's bytes, and the
        // draw that uses it. This is the VBO path RenderGlobal.renderSky takes (`skyVBO.drawArrays(7)` ->
        // VertexBuffer.drawArrays -> GlStateManager.glDrawArrays -> GL11.glDrawArrays), and it is the one entry point
        // here that produces geometry rather than state: answering it with a no-op is a hole in the picture, not a
        // state detail - measured as a GL-less world frame with no sky dome, no stars and no horizon plane while no
        // counter mentioned them.
        //
        // glEnableClientState/glDisableClientState are the same capability bits glEnable sets (GL_VERTEX_ARRAY is
        // 32884), so they share those semantics: glIsEnabled answers from the same record, as GL does.
        CeltiumGlStub.fn("glEnableClientState", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_ENABLE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glDisableClientState", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DISABLE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glVertexPointer", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_VERTEX_POINTER, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDrawArrays", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DRAW_ARRAYS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBegin", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BEGIN, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glEnd", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_END);
        // ---- the blend and colour-write state (BH-07, BH-10): the two the games' own wrappers reach through
        // OpenGlHelper rather than through a GlStateManager field, which is why a mixin hook could not see them.
        // Read from the original: `GlStateManager.blendFunc(int,int)` calls `GL11.glBlendFunc` directly, while both
        // `tryBlendFuncSeparate` overloads delegate to `OpenGlHelper.glBlendFunc`, which with OpenGL14 present (the
        // stub reports GL 4.6, so it is) calls `GL14.glBlendFuncSeparate`. The mixin hooks the enum overload only, so
        // the int overload - the one JsonBlendingMode and every Forge caller with plain ints uses - reached the
        // no-op table and the mirror kept the last enum call's factors. Recording here catches every caller of every
        // shape, including raw GL, for the same reason the cull capability is recorded here (BH-08).
        CeltiumGlStub.fn("glBlendFunc", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BLEND_FUNC, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBlendFuncSeparate", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BLEND_FUNC_SEPARATE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBlendEquation", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BLEND_EQUATION, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBlendEquationSeparate", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BLEND_EQUATION_SEPARATE, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        // BH-10: the Fancy-cloud depth-only pass reaches this through GlStateManager.colorMask, which is itself
        // cached and calls GL11.glColorMask only when the mask changes - so the stub is the one place that sees the
        // change that matters (false,false,false,false at RenderGlobal:1655).
        CeltiumGlStub.fn("glColorMask", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_COLOR_MASK, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        // ---- DSA create-functions: same out-array shape as the glGen* family
        CeltiumGlStub.fn("glCreateTextures", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateBuffers", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateFramebuffers", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateRenderbuffers", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateVertexArrays", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateSamplers", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateQueries", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateTransformFeedbacks", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateProgramPipelines", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
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
        CeltiumGlStub.fn("glGenFramebuffersEXT", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenRenderbuffersEXT", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteFramebuffersEXT", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteRenderbuffersEXT", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glIsFramebufferEXT", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsRenderbufferEXT", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glCheckFramebufferStatusEXT", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_FBO_COMPLETE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glGenFramebuffersARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGenRenderbuffersARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteFramebuffersARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteRenderbuffersARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glIsFramebufferARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glIsRenderbufferARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glCheckFramebufferStatusARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_FBO_COMPLETE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glGenBuffersARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteBuffersARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glIsBufferARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBindBufferARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BIND_BUFFER, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBufferDataARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BUFFER_DATA, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG, CeltiumGlStub.A_PTR, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glBufferSubDataARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_BUFFER_SUBDATA, CeltiumGlStub.A_INT, CeltiumGlStub.A_LONG, CeltiumGlStub.A_LONG, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glMapBufferARB", CeltiumGlStub.R_PTR, CeltiumGlStub.SEM_MAP, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glUnmapBufferARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_UNMAP, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glGetBufferParameterivARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCreateShaderObjectARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glCreateProgramObjectARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN);
        CeltiumGlStub.fn("glDeleteObjectARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glUseProgramObjectARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_USE_PROGRAM, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glGetUniformLocationARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_ZERO, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetObjectParameteriARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetInfoLogARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_INFO_LOG, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetActiveUniformARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetAttachedObjectsARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_STATUS, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glGetShaderSourceARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_INFO_LOG, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glCopyImageSubDataARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_COPY_IMAGE_SUB_DATA, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT,
                CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glActiveTextureARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_ACTIVE_TEXTURE, CeltiumGlStub.A_INT);
        CeltiumGlStub.fn("glGenQueriesARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_GEN, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glDeleteQueriesARB", CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_DELETE, CeltiumGlStub.A_INT, CeltiumGlStub.A_PTR);
        CeltiumGlStub.fn("glIsQueryARB", CeltiumGlStub.R_INT, CeltiumGlStub.SEM_IS_OBJECT, CeltiumGlStub.A_INT);
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
            CeltiumGlStub.fn(name, CeltiumGlStub.R_VOID, CeltiumGlStub.SEM_NOOP, new byte[] { CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR, CeltiumGlStub.A_PTR });
        }
    }
}
