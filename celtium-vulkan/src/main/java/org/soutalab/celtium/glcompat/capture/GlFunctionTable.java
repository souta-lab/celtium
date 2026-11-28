package org.soutalab.celtium.glcompat.capture;

/**
 * The stub's function table, extracted from {@code GlsmFrontend} unchanged in behaviour
 * (refactor/giant-split-4): every GL entry point the stub knows, grouped by the shape of its
 * answer - name allocation and queries, buffers and textures, state and draws, and the plain
 * no-ops - is registered here, in the stub's id order. Since S3-b E4-b2 the rows are built
 * through {@link DirectGlCaptureEntry#fn(String, byte, int, byte[])} into the capture entry's
 * table, which owns the name-to-id lookup and the dispatch the registered rows feed.
 * <p>
 * Registration order is behaviour: a function's id is its row index, so the four groups run in
 * the order the stub's static block has always run them, and a row is never reordered without
 * renumbering every id the trampolines cached.
 */
public final class GlFunctionTable {

    /**
     * Argument types as they arrive at the trampoline: every arg slot is a pointer to the value.
     * The encoding's home is this table since S3-b E4-b1 (it lived on the stub, whose fields now
     * alias these), because {@link GlTrampolines} reads the same encoding when it builds a row's
     * CIF and picks its upcall shape, and the capture entry takes the whole table at E4-b2.
     */
    public static final byte A_INT = 11;
    public static final byte A_LONG = 12;
    public static final byte A_PTR = 13;
    public static final byte A_FLOAT = 14;
    public static final byte A_DOUBLE = 15;
    public static final byte A_UINT = 16;

    /** Registers every row, in id order. Called once from the capture entry's static block. */
    public static void registerAll() {
        registerNamesAndQueries();
        registerBuffersAndTextures();
        registerStateAndDraws();
        registerNoops();
    }

    private static void registerNamesAndQueries() {
        // ---- names: every generator returns a fresh id; the same table's delete frees it for reporting only
        DirectGlCaptureEntry.fn("glGenTextures", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenBuffers", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenRenderbuffers", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenFramebuffers", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenVertexArrays", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenSamplers", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenQueries", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenLists", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT);
        DirectGlCaptureEntry.fn("glCreateShader", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT);
        DirectGlCaptureEntry.fn("glCreateProgram", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN);
        DirectGlCaptureEntry.fn("glGenSync", DirectGlCaptureEntry.R_LONG, DirectGlCaptureEntry.SEM_GEN);
        DirectGlCaptureEntry.fn("glFenceSync", DirectGlCaptureEntry.R_LONG, DirectGlCaptureEntry.SEM_GEN, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glGenTransformFeedbacks", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenProgramPipelines", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        // ---- queries that shape what the game thinks it has
        DirectGlCaptureEntry.fn("glGetString", DirectGlCaptureEntry.R_PTR, DirectGlCaptureEntry.SEM_GET_STRING, A_INT);
        DirectGlCaptureEntry.fn("glGetStringi", DirectGlCaptureEntry.R_PTR, DirectGlCaptureEntry.SEM_GET_STRINGI, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glGetError", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GET_ERROR);
        DirectGlCaptureEntry.fn("glIsEnabled", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_ENABLED, A_INT);   // GLboolean - byte, marshalled as int by the caller
        DirectGlCaptureEntry.fn("glIsEnabledi", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_ENABLED, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glIsTexture", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsBuffer", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsFramebuffer", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsRenderbuffer", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsVertexArray", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsShader", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsProgram", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsQuery", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsSampler", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsList", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsSync", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_LONG);
        DirectGlCaptureEntry.fn("glGetIntegerv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GET_INTEGERV, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetInteger64v", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GET_INTEGER64V, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetFloatv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GET_FLOATV, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetDoublev", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GET_FLOATV, A_INT, A_PTR);   // doubles read as floats is a known GL quirk - 4B each
        DirectGlCaptureEntry.fn("glGetBooleanv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GET_BOOLEANV, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glEnable", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_ENABLE, A_INT);
        // BH-08: the immediate path's cull state never reached the renderer because the face calls were no-ops and
        // nothing read the capability. Both are recorded here instead: the enable already lands in the enables map
        // with every other capability, and the face and winding get their own semantic case.
        DirectGlCaptureEntry.fn("glCullFace", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_CULL_FACE, A_INT);
        DirectGlCaptureEntry.fn("glFrontFace", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_FRONT_FACE, A_INT);
        DirectGlCaptureEntry.fn("glPolygonOffset", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_POLYGON_OFFSET, A_FLOAT, A_FLOAT);
        DirectGlCaptureEntry.fn("glDisable", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DISABLE, A_INT);
        // BH-09: Celeritas' own UI clips with GL11.glScissor directly (VintageDrawContext.enableScissor), so the
        // rectangle must be recorded rather than dropped - it was in the NOOP table, which is why a scroll list could
        // not be clipped. The enable bit is not a second mirror: scissorTest() reads the same enables map that
        // glEnable/glDisable write.
        DirectGlCaptureEntry.fn("glScissor", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_SCISSOR, A_INT, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glEnablei", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_ENABLE, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glDisablei", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DISABLE, A_INT, A_INT);
        // ---- deletion: retire names so glIs* answers go false (names are never reissued - GL does not require it)
        DirectGlCaptureEntry.fn("glDeleteTextures", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteBuffers", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteRenderbuffers", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteFramebuffers", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteVertexArrays", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteSamplers", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteQueries", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteLists", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glDeleteShader", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT);
        DirectGlCaptureEntry.fn("glDeleteProgram", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT);
        DirectGlCaptureEntry.fn("glDeleteSync", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_LONG);
        // ---- return-valued queries: a 0/garbage return would break callers that read it
        DirectGlCaptureEntry.fn("glCheckFramebufferStatus", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_FBO_COMPLETE, A_INT);
        DirectGlCaptureEntry.fn("glCheckNamedFramebufferStatus", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_FBO_COMPLETE, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glClientWaitSync", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_SIGNALED, A_LONG, A_INT, A_LONG);
        DirectGlCaptureEntry.fn("glRenderMode", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_ZERO, A_INT);
        DirectGlCaptureEntry.fn("glGetUniformLocation", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_ZERO, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetAttribLocation", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_ZERO, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetUniformBlockIndex", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_ZERO, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetProgramResourceIndex", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_ZERO, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateShaderProgramv", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTexLevelParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        // The DSA forms the modern renderer path actually calls: without these every size query answers 0, so
        // announcements are refused as "0x0" and mirrors are built 1x1 before the upload resizes them.
        DirectGlCaptureEntry.fn("glGetTextureLevelParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTextureLevelParameterfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTextureParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTextureParameterfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetNamedFramebufferAttachmentParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetFramebufferAttachmentParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTexLevelParameterfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetShaderiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetProgramiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetShaderInfoLog", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_INFO_LOG, A_INT, A_INT, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetProgramInfoLog", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_INFO_LOG, A_INT, A_INT, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetBufferParameteri64v", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetBufferParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetSynciv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_LONG, A_INT, A_INT, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetFramebufferParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetFramebufferAttachmentParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetRenderbufferParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetVertexAttribiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetVertexAttribPointerv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetUniformfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetUniformiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetActiveUniform", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR, A_PTR, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetActiveAttrib", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR, A_PTR, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetActiveUniformsiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetActiveUniformBlockiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetProgramResourceiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_INT, A_PTR, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetProgramResourceName", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_INFO_LOG, A_INT, A_INT, A_INT, A_INT, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetShaderSource", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_INFO_LOG, A_INT, A_INT, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetAttachedShaders", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetPointerv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetBufferPointerv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetQueryObjectiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetQueryObjectuiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetQueryObjecti64v", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetQueryObjectui64v", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
    }

    private static void registerBuffersAndTextures() {
        // ---- buffer storage: glBufferData allocates, glMapBuffer returns the storage address
        DirectGlCaptureEntry.fn("glBufferData", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BUFFER_DATA, A_INT, A_LONG, A_PTR, A_INT);
        DirectGlCaptureEntry.fn("glNamedBufferData", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BUFFER_DATA, A_INT, A_LONG, A_PTR, A_INT);
        DirectGlCaptureEntry.fn("glBufferStorage", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BUFFER_DATA, A_INT, A_LONG, A_PTR, A_INT);
        DirectGlCaptureEntry.fn("glNamedBufferStorage", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BUFFER_DATA, A_INT, A_LONG, A_PTR, A_INT);
        DirectGlCaptureEntry.fn("glMapBuffer", DirectGlCaptureEntry.R_PTR, DirectGlCaptureEntry.SEM_MAP, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glMapNamedBuffer", DirectGlCaptureEntry.R_PTR, DirectGlCaptureEntry.SEM_MAP, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glMapBufferRange", DirectGlCaptureEntry.R_PTR, DirectGlCaptureEntry.SEM_MAP, A_INT, A_LONG, A_LONG, A_INT);
        DirectGlCaptureEntry.fn("glMapNamedBufferRange", DirectGlCaptureEntry.R_PTR, DirectGlCaptureEntry.SEM_MAP, A_INT, A_LONG, A_LONG, A_INT);
        DirectGlCaptureEntry.fn("glUnmapBuffer", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_UNMAP, A_INT);
        DirectGlCaptureEntry.fn("glUnmapNamedBuffer", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_UNMAP, A_INT);
        DirectGlCaptureEntry.fn("glBindBuffer", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BIND_BUFFER, A_INT, A_INT);
        // ---- texture bindings/images: size tracking feeds glGetTexLevelParameter (FML probes the max texture
        // size by uploading proxy images) and the pixel capture the Vulkan mirrors are built from.
        DirectGlCaptureEntry.fn("glBindTexture", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BIND_TEXTURE, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glBindImageTexture", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BIND_IMAGE_TEXTURE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glActiveTexture", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_ACTIVE_TEXTURE, A_INT);
        DirectGlCaptureEntry.fn("glTexImage1D", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEXIMAGE_PIXELS, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTexImage2D", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEXIMAGE_PIXELS, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        // glTexImage3D carries depth between height and border: (target,level,internal,w,h,depth,border,fmt,type,pixels)
        DirectGlCaptureEntry.fn("glTexImage3D", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEXIMAGE_PIXELS, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTexStorage2D", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glTexStorage3D", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glCompressedTexImage2D", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_COMPRESSED_TEXIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTexSubImage2D", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEXSUBIMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glPixelStorei", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_PIXEL_STORE, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glViewport", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_VIEWPORT, A_INT, A_INT, A_INT, A_INT);
        // Texture-environment state. The stub used to no-op all of it, so the two queries the renderer asks - the
        // combine mode and the environment colour, both on the lightmap unit - returned zero and the hurt flash was
        // invisible to the instrument as well as to the frame (measured by entity-models: the reference shifts the
        // zombie head from (78.9,123.7,87.7) to (117.3,105.0,79.4) and ours moves by at most 1.8).
        DirectGlCaptureEntry.fn("glFogi", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_FOG_I, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glFogf", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_FOG_F, A_INT, A_FLOAT);
        DirectGlCaptureEntry.fn("glFogfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_FOG_FV, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glFogiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_FOG_IV, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTexEnvi", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_ENV_I, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glTexEnviv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_ENV_I, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTexEnvf", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_ENV_F, A_INT, A_INT, A_FLOAT);
        DirectGlCaptureEntry.fn("glTexEnvfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_ENV_FV, A_INT, A_INT, A_PTR);
        // LWJGL2's GlStateManager.glTexEnv(int,int,FloatBuffer) arrives under the bare name, which is the fv variant:
        // RenderLivingBase.setBrightness writes the hurt/team env colour through exactly this call, and while it sat
        // in the noop list texEnvColor stayed zero - the combine fired but the mix factor was black at 0.
        DirectGlCaptureEntry.fn("glTexEnv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_ENV_FV, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTexEnvi", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GET_TEX_ENV_I, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTexEnviv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GET_TEX_ENV_I, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTexEnvfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GET_TEX_ENV_FV, A_INT, A_INT, A_PTR);
        // Texture parameters: the filter and wrap state GL keeps per texture object. These used to be in the no-op
        // list, so every texture answered the same hardcoded value and the real one was gone - measured as the
        // options background sampling CLAMP_TO_EDGE where vanilla tiles it with REPEAT (GuiOptions.drawBackground
        // and Cleanroom's patched GuiSlot.drawContainerBackground both draw it with UVs up to height/32), and the
        // sky faces likewise. Recorded per texture *name* rather than per binding because the queries that need it
        // (the mirror's wrap read, the DSA glGetTextureParameter*) name the texture, not the unit.
        DirectGlCaptureEntry.fn("glTexParameteri", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_I, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glTexParameterf", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_I, A_INT, A_INT, A_FLOAT);
        DirectGlCaptureEntry.fn("glTexParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_IV, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTexParameterfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_IV, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTexParameterIiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_IV, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTexParameterIuiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_IV, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTexParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTexParameterfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTexParameterIiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetTexParameterIuiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        // The DSA forms name the texture directly: the modern mirror path asks with glGetTextureParameteri (LWJGL
        // answers that over glGetTextureParameteriv) and sets with glTextureParameteri.
        DirectGlCaptureEntry.fn("glTextureParameteri", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_I_DSA, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glTextureParameterf", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_I_DSA, A_INT, A_INT, A_FLOAT);
        DirectGlCaptureEntry.fn("glTextureParameteriv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_IV_DSA, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTextureParameterfv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_IV_DSA, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTextureParameterIiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_IV_DSA, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glTextureParameterIuiv", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_TEX_PARAM_IV_DSA, A_INT, A_INT, A_PTR);
        // Matrix multiplication that never goes through GlStateManager. Forge's item transform is the measured case:
        // ForgeHooksClient.handleCameraTransforms -> multiplyCurrentGlMatrix -> GL11.glMultMatrix(matrixBuf)
        // (ForgeHooksClient.java:425-455), so the item's 30/225-degree rotation reached the stub's no-op list while
        // every GlStateManager-routed matrix call reached the mirror - the mirror kept the GUI ortho as the group's
        // modelview and all six faces of a block projected onto one square (the user's flat slot icon). The same entry
        // point is what the GLU perspective helper uses: org.lwjglx.util.glu.Project.gluPerspective calls
        // GL11.glMultMatrixf at two sites (javap of lwjglxx-1.1.22), so this hook is also the projection channel's
        // missing link - glMatrixMode is recorded here so the census says which stack the multiply landed on.
        DirectGlCaptureEntry.fn("glMultMatrixf", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_MULT_MATRIX, A_PTR);
        DirectGlCaptureEntry.fn("glMultMatrixd", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_MULT_MATRIX, A_PTR);
        DirectGlCaptureEntry.fn("glMatrixMode", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_MATRIX_MODE, A_INT);
    }

    private static void registerStateAndDraws() {
        // Copy-from-framebuffer. The main menu's panorama is built with these: renderSkybox draws the six 256x256
        // cube faces into the window and copies each into the skybox texture, and the blur passes copy their own
        // targets. Registered rather than left in the no-op list so the request is named and counted: under GL-less
        // there is no GL framebuffer to read the pixels from, so what is missing is a readback of the renderer's own
        // image for the bound read framebuffer, which is a different component's resource.
        DirectGlCaptureEntry.fn("glCopyTexImage2D", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_COPY_TEX_IMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glCopyTexSubImage2D", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_COPY_TEX_SUB_IMAGE, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        // Texture-to-texture copy: unlike the framebuffer copies above, both levels live in the stub's own
        // capture, so this one is served in full - it is FoamFix's fast animation path (see copyImageSubData).
        DirectGlCaptureEntry.fn("glCopyImageSubData", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_COPY_IMAGE_SUB_DATA, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT,
                A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glBlitFramebuffer", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BLIT_FRAMEBUFFER, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT,
                A_INT, A_INT);
        // glReadPixels is the same question the copies answer, asked into a client buffer: the bound read framebuffer's
        // rectangle from the image the renderer owns. It used to sit in the no-op list, which left every caller's
        // buffer zeroed - the fixture's format-2 readback measured "all mismatch" and ScreenShotHelper's screenshots
        // came out blank - so it is registered and served now.
        DirectGlCaptureEntry.fn("glReadPixels", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_READ_PIXELS, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glBindFramebuffer", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BIND_FRAMEBUFFER, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glBindFramebufferEXT", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BIND_FRAMEBUFFER, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glBindFramebufferARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BIND_FRAMEBUFFER, A_INT, A_INT);
        // ---- clears: with no GL framebuffer to clear, the request is recorded for the owned frame image. Without
        // this the image's depth holds undefined values for the whole session and every depth-tested draw fails.
        DirectGlCaptureEntry.fn("glClear", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_CLEAR, A_INT);
        DirectGlCaptureEntry.fn("glClearColor", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_CLEAR_COLOR, A_FLOAT, A_FLOAT, A_FLOAT, A_FLOAT);
        DirectGlCaptureEntry.fn("glClearDepth", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_CLEAR_DEPTH, A_DOUBLE);
        DirectGlCaptureEntry.fn("glClearDepthf", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_CLEAR_DEPTH, A_FLOAT);
        // ---- state the getters answer from: recorded rather than left at the no-op default
        DirectGlCaptureEntry.fn("glLineWidth", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_LINE_WIDTH, A_FLOAT);
        DirectGlCaptureEntry.fn("glUseProgram", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_USE_PROGRAM, A_INT);
        DirectGlCaptureEntry.fn("glNewList", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_NEW_LIST, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glEndList", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_END_LIST);
        DirectGlCaptureEntry.fn("glCallList", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_CALL_LIST, A_INT);
        DirectGlCaptureEntry.fn("glListBase", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_LIST_BASE, A_INT);
        DirectGlCaptureEntry.fn("glCallLists", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_CALL_LISTS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glBindVertexArray", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BIND_VERTEX_ARRAY, A_INT);
        DirectGlCaptureEntry.fn("glBufferSubData", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BUFFER_SUBDATA, A_INT, A_LONG, A_LONG, A_PTR);
        // ---- client-array drawing: the pointer state that says how to read the bound array buffer's bytes, and the
        // draw that uses it. This is the VBO path RenderGlobal.renderSky takes (`skyVBO.drawArrays(7)` ->
        // VertexBuffer.drawArrays -> GlStateManager.glDrawArrays -> GL11.glDrawArrays), and it is the one entry point
        // here that produces geometry rather than state: answering it with a no-op is a hole in the picture, not a
        // state detail - measured as a GL-less world frame with no sky dome, no stars and no horizon plane while no
        // counter mentioned them.
        //
        // glEnableClientState/glDisableClientState are the same capability bits glEnable sets (GL_VERTEX_ARRAY is
        // 32884), so they share those semantics: glIsEnabled answers from the same record, as GL does.
        DirectGlCaptureEntry.fn("glEnableClientState", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_ENABLE, A_INT);
        DirectGlCaptureEntry.fn("glDisableClientState", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DISABLE, A_INT);
        DirectGlCaptureEntry.fn("glVertexPointer", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_VERTEX_POINTER, A_INT, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDrawArrays", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DRAW_ARRAYS, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glBegin", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BEGIN, A_INT);
        DirectGlCaptureEntry.fn("glEnd", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_END);
        // ---- the blend and colour-write state (BH-07, BH-10): the two the games' own wrappers reach through
        // OpenGlHelper rather than through a GlStateManager field, which is why a mixin hook could not see them.
        // Read from the original: `GlStateManager.blendFunc(int,int)` calls `GL11.glBlendFunc` directly, while both
        // `tryBlendFuncSeparate` overloads delegate to `OpenGlHelper.glBlendFunc`, which with OpenGL14 present (the
        // stub reports GL 4.6, so it is) calls `GL14.glBlendFuncSeparate`. The mixin hooks the enum overload only, so
        // the int overload - the one JsonBlendingMode and every Forge caller with plain ints uses - reached the
        // no-op table and the mirror kept the last enum call's factors. Recording here catches every caller of every
        // shape, including raw GL, for the same reason the cull capability is recorded here (BH-08).
        DirectGlCaptureEntry.fn("glBlendFunc", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BLEND_FUNC, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glBlendFuncSeparate", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BLEND_FUNC_SEPARATE, A_INT, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glBlendEquation", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BLEND_EQUATION, A_INT);
        DirectGlCaptureEntry.fn("glBlendEquationSeparate", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BLEND_EQUATION_SEPARATE, A_INT, A_INT);
        // BH-10: the Fancy-cloud depth-only pass reaches this through GlStateManager.colorMask, which is itself
        // cached and calls GL11.glColorMask only when the mask changes - so the stub is the one place that sees the
        // change that matters (false,false,false,false at RenderGlobal:1655).
        DirectGlCaptureEntry.fn("glColorMask", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_COLOR_MASK, A_INT, A_INT, A_INT, A_INT);
        // ---- DSA create-functions: same out-array shape as the glGen* family
        DirectGlCaptureEntry.fn("glCreateTextures", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GEN, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateBuffers", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateFramebuffers", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateRenderbuffers", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateVertexArrays", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateSamplers", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateQueries", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GEN, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateTransformFeedbacks", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateProgramPipelines", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
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
        DirectGlCaptureEntry.fn("glGenFramebuffersEXT", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenRenderbuffersEXT", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteFramebuffersEXT", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteRenderbuffersEXT", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glIsFramebufferEXT", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsRenderbufferEXT", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glCheckFramebufferStatusEXT", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_FBO_COMPLETE, A_INT);
        DirectGlCaptureEntry.fn("glGenFramebuffersARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGenRenderbuffersARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteFramebuffersARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteRenderbuffersARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glIsFramebufferARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glIsRenderbufferARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glCheckFramebufferStatusARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_FBO_COMPLETE, A_INT);
        DirectGlCaptureEntry.fn("glGenBuffersARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteBuffersARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glIsBufferARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
        DirectGlCaptureEntry.fn("glBindBufferARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BIND_BUFFER, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glBufferDataARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BUFFER_DATA, A_INT, A_LONG, A_PTR, A_INT);
        DirectGlCaptureEntry.fn("glBufferSubDataARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_BUFFER_SUBDATA, A_INT, A_LONG, A_LONG, A_PTR);
        DirectGlCaptureEntry.fn("glMapBufferARB", DirectGlCaptureEntry.R_PTR, DirectGlCaptureEntry.SEM_MAP, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glUnmapBufferARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_UNMAP, A_INT);
        DirectGlCaptureEntry.fn("glGetBufferParameterivARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glCreateShaderObjectARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT);
        DirectGlCaptureEntry.fn("glCreateProgramObjectARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN);
        DirectGlCaptureEntry.fn("glDeleteObjectARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT);
        DirectGlCaptureEntry.fn("glUseProgramObjectARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_USE_PROGRAM, A_INT);
        DirectGlCaptureEntry.fn("glGetUniformLocationARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_ZERO, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetObjectParameteriARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glGetInfoLogARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_INFO_LOG, A_INT, A_INT, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetActiveUniformARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_INT, A_PTR, A_PTR, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetAttachedObjectsARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_STATUS, A_INT, A_INT, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glGetShaderSourceARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_INFO_LOG, A_INT, A_INT, A_PTR, A_PTR);
        DirectGlCaptureEntry.fn("glCopyImageSubDataARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_COPY_IMAGE_SUB_DATA, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT,
                A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT, A_INT);
        DirectGlCaptureEntry.fn("glActiveTextureARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_ACTIVE_TEXTURE, A_INT);
        DirectGlCaptureEntry.fn("glGenQueriesARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_GEN, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glDeleteQueriesARB", DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_DELETE, A_INT, A_PTR);
        DirectGlCaptureEntry.fn("glIsQueryARB", DirectGlCaptureEntry.R_INT, DirectGlCaptureEntry.SEM_IS_OBJECT, A_INT);
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
            DirectGlCaptureEntry.fn(name, DirectGlCaptureEntry.R_VOID, DirectGlCaptureEntry.SEM_NOOP, new byte[] { A_PTR, A_PTR, A_PTR, A_PTR, A_PTR, A_PTR, A_PTR, A_PTR });
        }
    }
}
