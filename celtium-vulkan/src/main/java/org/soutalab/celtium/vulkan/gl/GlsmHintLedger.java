package org.soutalab.celtium.vulkan.gl;

import java.util.HashMap;
import java.util.Map;

/**
 * GLSM stage 3, GAP-HINT-1 (census I-1/I-2/I-3/I-6): the receiving ledger for the Hint
 * doors, the second surface beside the 476-name function table.
 * <p>
 * CeltiumGllessHint carries 275 public statics on the current alpha tip (266 doors in
 * the census's passtail accounting). Those doors are entry points, not owners: every
 * door that lands guest state must name the GLSM owner it writes, and this ledger is
 * that naming - door name to owner id, with an arrival count so the merge phase can
 * see which doors real packs actually use. The three installation facts the census
 * groups here (coremod install, the FunctionProvider route, the capabilities-first
 * install of 427 extension names) are recorded as owned facts of this ledger too:
 * they are part of the surface the stub's deletion must account for.
 */
public final class GlsmHintLedger {
    private static final GlsmHintLedger CURRENT = new GlsmHintLedger();

    public static GlsmHintLedger current() { return CURRENT; }

    /** Door name -> the GLSM owner id it writes (e.g. "enable", "viewport", "clear"). */
    private final Map<String, String> doorOwners = new HashMap<String, String>();
    private final Map<String, Long> doorArrivals = new HashMap<String, Long>();
    private boolean stubInstalled;
    private boolean functionProviderInstalled;
    private int capabilitiesExtensions;

    private GlsmHintLedger() {
    }

    static {
        // Doors whose receiving end this stage-3 landing rewired, registered with the owner
        // they now write. The remaining doors register as their routes land; arrivals at an
        // unregistered door are the ledger's unroutedArrivals() leftover list for merge.
        CURRENT.registerDoor("glViewport", "viewport");
        CURRENT.registerDoor("glClearColor", "clear");
        CURRENT.registerDoor("glClearDepth", "clear");
        CURRENT.registerDoor("glDepthMask", "enable");
        CURRENT.registerDoor("glDepthFunc", "raster");
        CURRENT.registerDoor("glLineWidth", "immediate");
        CURRENT.registerDoor("glPointSize", "immediate");
        CURRENT.registerDoor("glBindRenderbuffer", "clientArray");
        CURRENT.registerDoor("glRenderbufferStorage", "clientArray");
        CURRENT.registerDoor("glListBase", "displayList");
        CURRENT.registerDoor("glPushAttrib", "attribStacks");
        CURRENT.registerDoor("glPopAttrib", "attribStacks");
        CURRENT.registerDoor("glPushClientAttrib", "attribStacks");
        CURRENT.registerDoor("glPopClientAttrib", "attribStacks");
        // M-7 (HINT-1 remainder): the doors below are registered with the owner their receiving end
        // already writes or reads on the routes base. Doors whose receiving end has no GLSM owner
        // yet (texture capture/parameter ledgers, VAO attribute records, matrix mode, light ambient,
        // draw/readback processing, constant answers, Hint-local census/window helpers) stay
        // unregistered on purpose; the M-7 record lists each of them with its reason.
        // Name generation, deletion and existence: GlsmNameLedger (stage-3 ST-10) owns the live-name and sync ledgers these doors feed or read.
        CURRENT.registerDoor("glGenNames", "nameLedger");
        CURRENT.registerDoor("glGenName", "nameLedger");
        CURRENT.registerDoor("glDeleteObjects", "nameLedger");
        CURRENT.registerDoor("glDeleteObject", "nameLedger");
        CURRENT.registerDoor("glDeleteTextures", "nameLedger");
        CURRENT.registerDoor("glDeleteFramebuffers", "nameLedger");
        CURRENT.registerDoor("glDeleteSync", "nameLedger");
        CURRENT.registerDoor("glIsList", "nameLedger");
        CURRENT.registerDoor("glIsObject", "nameLedger");
        CURRENT.registerDoor("glIsSync", "nameLedger");
        // Shader/program surface: sources, stages and the current program live in GlsmShaderLedger (PROG-1); the program machine that consumes them (ModProgramRegistry) reads that ledger. Uniform writes belong to the bound program of the same ledger pair.
        CURRENT.registerDoor("glUseProgram", "shader");
        CURRENT.registerDoor("glCreateShader", "shader");
        CURRENT.registerDoor("glShaderSource", "shader");
        CURRENT.registerDoor("glAttachShader", "shader");
        CURRENT.registerDoor("glLinkProgram", "shader");
        CURRENT.registerDoor("glDeleteProgram", "shader");
        CURRENT.registerDoor("glUniform1f", "shader");
        CURRENT.registerDoor("glUniform2f", "shader");
        CURRENT.registerDoor("glUniform3f", "shader");
        CURRENT.registerDoor("glUniform4f", "shader");
        CURRENT.registerDoor("glUniform1i", "shader");
        CURRENT.registerDoor("glUniform2i", "shader");
        CURRENT.registerDoor("glUniform3i", "shader");
        CURRENT.registerDoor("glUniform4i", "shader");
        CURRENT.registerDoor("glUniformFloatv", "shader");
        CURRENT.registerDoor("glUniformIntv", "shader");
        CURRENT.registerDoor("glGetShaderInfoLog", "shader");
        CURRENT.registerDoor("glGetProgramInfoLog", "shader");
        CURRENT.registerDoor("glGetInfoLogARB", "shader");
        CURRENT.registerDoor("glGetShaderSource", "shader");
        CURRENT.registerDoor("glGetAttachedShaders", "shader");
        CURRENT.registerDoor("glGetActiveObject", "shader");
        CURRENT.registerDoor("glGetActiveUniformInt", "shader");
        CURRENT.registerDoor("glGetActiveUniforms", "shader");
        CURRENT.registerDoor("glGetActiveUniformName", "shader");
        CURRENT.registerDoor("glGetProgramResourceName", "shader");
        CURRENT.registerDoor("glGetProgramResourceLength", "shader");
        CURRENT.registerDoor("glGetProgramBinary", "shader");
        CURRENT.registerDoor("glGetShaderPrecisionFormat", "shader");
        CURRENT.registerDoor("glShaderObjectParameter", "shader");
        CURRENT.registerDoor("glGetShaderProgramiv", "shader");
        CURRENT.registerDoor("glGetShaderProgrami", "shader");
        // Buffer storage, binding-fed data and pixel store: GlsmBufferState (ST-2, with S-5 pixel store and S-24 readable ranges) is the receiving end of these doors.
        CURRENT.registerDoor("glBindBuffer", "buffer");
        CURRENT.registerDoor("glBufferData", "buffer");
        CURRENT.registerDoor("glBufferStorage", "buffer");
        CURRENT.registerDoor("glBufferSubData", "buffer");
        CURRENT.registerDoor("glNamedBufferData", "buffer");
        CURRENT.registerDoor("glNamedBufferStorage", "buffer");
        CURRENT.registerDoor("glNamedBufferSubData", "buffer");
        CURRENT.registerDoor("glCopyBufferSubData", "buffer");
        CURRENT.registerDoor("glCopyNamedBufferSubData", "buffer");
        CURRENT.registerDoor("glGetBufferSubData", "buffer");
        CURRENT.registerDoor("glGetNamedBufferSubData", "buffer");
        CURRENT.registerDoor("glMapBuffer", "buffer");
        CURRENT.registerDoor("glMapNamedBuffer", "buffer");
        CURRENT.registerDoor("glUnmapBuffer", "buffer");
        CURRENT.registerDoor("glUnmapNamedBuffer", "buffer");
        CURRENT.registerDoor("glClearBuffer", "buffer");
        CURRENT.registerDoor("glGetNamedBufferParameter", "buffer");
        CURRENT.registerDoor("glGetBufferParameteriv", "buffer");
        CURRENT.registerDoor("glGetBufferParameteri64v", "buffer");
        CURRENT.registerDoor("glPixelStorei", "buffer");
        CURRENT.registerDoor("glPixelStoref", "buffer");
        // Display lists beyond glListBase: GlsmDisplayListLedger (ST-7) owns the list ledgers, compiling/executing state and counters these doors move.
        CURRENT.registerDoor("glNewList", "displayList");
        CURRENT.registerDoor("nativeNewList", "displayList");
        CURRENT.registerDoor("glEndList", "displayList");
        CURRENT.registerDoor("nativeEndList", "displayList");
        CURRENT.registerDoor("glCallList", "displayList");
        CURRENT.registerDoor("glCallLists", "displayList");
        CURRENT.registerDoor("glDeleteLists", "displayList");
        // Legacy immediate current values and geometry feed: GlsmImmediateState (ST-9/S-14) holds the open/mode/current-colour state; every colour spelling funnels into the glColor4f write of that same current colour.
        CURRENT.registerDoor("glLegacyBegin", "immediate");
        CURRENT.registerDoor("glLegacyEnd", "immediate");
        CURRENT.registerDoor("glLegacyVertex3f", "immediate");
        CURRENT.registerDoor("glLegacyVertex2f", "immediate");
        CURRENT.registerDoor("glLegacyVertex2d", "immediate");
        CURRENT.registerDoor("glLegacyVertex3d", "immediate");
        CURRENT.registerDoor("glLegacyVertex4f", "immediate");
        CURRENT.registerDoor("glLegacyVertex4d", "immediate");
        CURRENT.registerDoor("glLegacyVertex2i", "immediate");
        CURRENT.registerDoor("glLegacyVertex3i", "immediate");
        CURRENT.registerDoor("glLegacyVertex4i", "immediate");
        CURRENT.registerDoor("glLegacyVertex2s", "immediate");
        CURRENT.registerDoor("glLegacyVertex3s", "immediate");
        CURRENT.registerDoor("glLegacyTexCoord2f", "immediate");
        CURRENT.registerDoor("glLegacyTexCoord2d", "immediate");
        CURRENT.registerDoor("glLegacyTexCoord2i", "immediate");
        CURRENT.registerDoor("glLegacyTexCoord2s", "immediate");
        CURRENT.registerDoor("glLegacyMultiTexCoord2f", "immediate");
        CURRENT.registerDoor("glLegacyTexCoordF", "immediate");
        CURRENT.registerDoor("glLegacyTexCoordD", "immediate");
        CURRENT.registerDoor("glLegacyTexCoordI", "immediate");
        CURRENT.registerDoor("glLegacyMultiTexCoordF", "immediate");
        CURRENT.registerDoor("glLegacyMultiTexCoordD", "immediate");
        CURRENT.registerDoor("glLegacyMultiTexCoordI", "immediate");
        CURRENT.registerDoor("glLegacyTexCoordV", "immediate");
        CURRENT.registerDoor("glLegacyMultiTexCoordV", "immediate");
        CURRENT.registerDoor("glLegacyRectf", "immediate");
        CURRENT.registerDoor("glLegacyRectd", "immediate");
        CURRENT.registerDoor("glLegacyRecti", "immediate");
        CURRENT.registerDoor("glLegacyRects", "immediate");
        CURRENT.registerDoor("glColor4f", "immediate");
        CURRENT.registerDoor("glColor4ub", "immediate");
        CURRENT.registerDoor("glColor3fv", "immediate");
        CURRENT.registerDoor("glColor4fv", "immediate");
        CURRENT.registerDoor("glColor3bv", "immediate");
        CURRENT.registerDoor("glColor4bv", "immediate");
        CURRENT.registerDoor("glColor3ubv", "immediate");
        CURRENT.registerDoor("glColor4ubv", "immediate");
        CURRENT.registerDoor("glColor4b", "immediate");
        CURRENT.registerDoor("glColor4s", "immediate");
        CURRENT.registerDoor("glColor4us", "immediate");
        CURRENT.registerDoor("glColor4i", "immediate");
        CURRENT.registerDoor("glColor4ui", "immediate");
        CURRENT.registerDoor("glColor3sv", "immediate");
        CURRENT.registerDoor("glColor4sv", "immediate");
        CURRENT.registerDoor("glColor3usv", "immediate");
        CURRENT.registerDoor("glColor4usv", "immediate");
        CURRENT.registerDoor("glColor3iv", "immediate");
        CURRENT.registerDoor("glColor4iv", "immediate");
        CURRENT.registerDoor("glColor3uiv", "immediate");
        CURRENT.registerDoor("glColor4uiv", "immediate");
        CURRENT.registerDoor("glColor3dv", "immediate");
        CURRENT.registerDoor("glColor4dv", "immediate");
        // Fog parameters: ImmediateFogState is the single owner (census S-18).
        CURRENT.registerDoor("glFogf", "fog");
        CURRENT.registerDoor("glFogi", "fog");
        CURRENT.registerDoor("glFogfv", "fog");
        CURRENT.registerDoor("glFogiv", "fog");
        // Texture environment: NativeTextureEnvironment is the single owner (census S-19).
        CURRENT.registerDoor("glTexEnvi", "textureEnvironment");
        CURRENT.registerDoor("glTexEnvf", "textureEnvironment");
        CURRENT.registerDoor("glTexEnvPtr", "textureEnvironment");
        CURRENT.registerDoor("glGetTexEnvI", "textureEnvironment");
        CURRENT.registerDoor("glGetTexEnvInt", "textureEnvironment");
        CURRENT.registerDoor("glGetTexEnvFloats", "textureEnvironment");
        // Texture binding and active unit: NativeTextureBindings is the single owner (census S-4 binding part).
        CURRENT.registerDoor("glBindTexture", "textureBindings");
        CURRENT.registerDoor("glActiveTexture", "textureBindings");
        // Indexed raster state at target 0: the same NativeRasterState the unindexed routes write (census S-16), via NativeRasterAdapter.
        CURRENT.registerDoor("glBlendFuncSeparateIndexed", "raster");
        CURRENT.registerDoor("glBlendEquationIndexed", "raster");
        CURRENT.registerDoor("glBlendEquationSeparateIndexed", "raster");
        CURRENT.registerDoor("glColorMaskIndexed", "raster");
        // Enable state reads and the indexed enable at target 0: GlsmEnableState (ST-1) is what glIsEnabled answers from (through GlsmQueryFacade) and what the client-state record writes.
        CURRENT.registerDoor("glIsEnabled", "enable");
        CURRENT.registerDoor("glEnableIndexed", "enable");
        CURRENT.registerDoor("glIsEnabledIndexed", "enable");
        // Client arrays and renderbuffer record: GlsmClientArrayState (ST-8) owns the pointer tuples and the renderbuffer binding/dimensions the parameter query reads.
        CURRENT.registerDoor("glEnableClientState", "clientArray");
        CURRENT.registerDoor("glDisableClientState", "clientArray");
        CURRENT.registerDoor("glVertexPointer", "clientArray");
        CURRENT.registerDoor("glTexCoordPointer", "clientArray");
        CURRENT.registerDoor("glColorPointer", "clientArray");
        CURRENT.registerDoor("glNormalPointer", "clientArray");
        CURRENT.registerDoor("glGetRenderbufferParameter", "clientArray");
        // Clear entry points beyond the colour/depth setters: GlsmClearState (ST-4) owns the current values and pending latch these doors feed.
        CURRENT.registerDoor("glClear", "clear");
        CURRENT.registerDoor("glClearBufferfv", "clear");
        CURRENT.registerDoor("glClearBufferfi", "clear");
        // State queries whose answers GlsmQueryFacade serves from the moved owners (Q-1 rewired part). Constant-only answers are deliberately not registered here; they are listed in the M-7 record.
        CURRENT.registerDoor("glGetIntegerv", "queryFacade");
        CURRENT.registerDoor("glGetFloatv", "queryFacade");
        CURRENT.registerDoor("glGetDoublev", "queryFacade");
        CURRENT.registerDoor("glGetBooleanv", "queryFacade");
        CURRENT.registerDoor("glGetBooleanIndexed", "queryFacade");
        CURRENT.registerDoor("glGetIntegerIndexed", "queryFacade");
        CURRENT.registerDoor("glGetInteger64", "queryFacade");
        // Framebuffer attachments: GlsmFramebufferAttachments (ST-3) owns the attachment maps these doors write and read.
        CURRENT.registerDoor("glNamedFramebufferTexture", "framebufferAttachments");
        CURRENT.registerDoor("glFramebufferTexture2D", "framebufferAttachments");
        CURRENT.registerDoor("glGetFramebufferAttachmentParameteriv", "framebufferAttachments");
        CURRENT.registerDoor("glGetNamedFramebufferAttachmentParameteriv", "framebufferAttachments");
    }

    public void registerDoor(String door, String ownerId) { this.doorOwners.put(door, ownerId); }

    /** @return the owner id a door writes, or null when the door has no GLSM receiving end yet. */
    public String ownerOf(String door) { return this.doorOwners.get(door); }

    public void noteArrival(String door) {
        Long count = this.doorArrivals.get(door);
        this.doorArrivals.put(door, Long.valueOf(count == null ? 1L : count.longValue() + 1L));
    }

    public long arrivals(String door) {
        Long count = this.doorArrivals.get(door);
        return count == null ? 0L : count.longValue();
    }

    public int registeredDoors() { return this.doorOwners.size(); }

    /** Doors seen arriving with no registered owner - the merge phase's leftover list. */
    public int unroutedArrivals() {
        int total = 0;
        for (Map.Entry<String, Long> entry : this.doorArrivals.entrySet()) {
            if (!this.doorOwners.containsKey(entry.getKey())) total += entry.getValue().intValue();
        }
        return total;
    }

    public void noteStubInstalled() { this.stubInstalled = true; }
    public boolean stubInstalled() { return this.stubInstalled; }

    public void noteFunctionProviderInstalled() { this.functionProviderInstalled = true; }
    public boolean functionProviderInstalled() { return this.functionProviderInstalled; }

    public void noteCapabilitiesInstalled(int extensionCount) {
        this.capabilitiesExtensions = extensionCount;
    }

    public int capabilitiesExtensions() { return this.capabilitiesExtensions; }
}
