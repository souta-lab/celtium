package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryUtil;

/**
 * Legacy immediate-mode geometry ({@code glBegin}/{@code glVertex*}/{@code glEnd}) collected into the vertex layouts
 * the immediate path already draws.
 * <p>
 * Why this is a class of its own: the capture used to live inside {@link CeltiumGlStub} as a handful of fields, which
 * made the one half of legacy immediate mode that can be checked without a GPU - "which vertices, in which layout, for
 * which mode" - reachable only by starting a game. The packing rules here are pure arithmetic over a direct buffer, so
 * they are pinned by {@code LegacyImmediateCaptureTest} instead. The stub keeps the GL surface and the backend keeps
 * the drawing; this owns only the vertex assembly.
 * <h2>What the capture promises</h2>
 * <ul>
 * <li><b>Two layouts, chosen by the caller at {@code glBegin}.</b> {@code POSITION_TEX_COLOR} (format 2, stride 24:
 * position 3 floats, texture 2 floats, colour 4 unsigned bytes) when {@code GL_TEXTURE_2D} was enabled for the run, and
 * {@code POSITION_COLOR} (format 1, stride 16: position 3 floats, colour 4 unsigned bytes) otherwise. Both are
 * existing immediate layouts, so the matrices, the fog, the bound texture and the group colour come from the paths that
 * already produce them - nothing new is bound and no new pipeline is asked for.</li>
 * <li><b>GL's current-value semantics.</b> A texture coordinate and a colour persist until they are set again, exactly
 * as GL keeps one current texture coordinate and one current colour; they are captured per vertex, not per run.</li>
 * <li><b>{@code w} is divided out.</b> {@code glVertex4f}/{@code glVertex4d} carry a homogeneous coordinate and GL
 * projects by it, so a vertex with {@code w != 1} is stored as {@code x/w, y/w, z/w}. A {@code w} of zero has no
 * finite position; the run is refused by name rather than drawn with an invented one.</li>
 * <li><b>Refusals are counted by reason, never silently dropped.</b> A second {@code glBegin} before {@code glEnd}, a
 * layout the renderer has no draw for (the gate), more than {@link #MAX_VERTICES} vertices in one run, and a sink that
 * will not take the run all leave the run undrawn and land in a named counter. Under GL-less a refusal draws nothing,
 * so the count is the only way the loss is visible.</li>
 * <li><b>Geometry inside a display-list compile is recorded, not refused.</b> {@code glBegin} is legal inside
 * {@code glNewList}/{@code glEndList}, and a mod that draws this way compiles its geometry into the list and replays it
 * with {@code glCallList} - GregTech's tool rotation marker is exactly that ({@code ToolEventHandlers}, measured from
 * its class file: {@code glNewList(4864=GL_COMPILE)} + {@code glBegin(GL_LINE_LOOP)} + 121 {@code glVertex3d} +
 * {@code glEnd} + {@code glEndList}, then {@code glCallList} per frame). Drawn at compile time it would appear where
 * the compile matrix put it, which is not where the list is used; refused (the previous rule) the list stays empty and
 * every replay draws nothing. The run is therefore handed to {@link CompiledRunSink}, which owns a copy of the
 * vertices and replays them with the matrix and state current at {@code glCallList} time.</li>
 * <li><b>The trailing vertices of an incomplete primitive are ignored, as GL ignores them.</b> A run that contains at
 * least one complete primitive is cut to whole primitives before it is offered ({@link #wholePrimitives}) and the cut
 * vertices are counted; a run with none is offered unchanged so the renderer's own refusal names it. Refusing the
 * whole run instead - the rule the renderer's index expansion keeps - lost the complete quad a mod wrote before one
 * stray vertex.</li>
 * </ul>
 * <p>
 * Not captured, deliberately: normals, and multi-texture coordinates on units other than 0. Every mode here is drawn
 * through an unlit immediate layout, so a normal would have no consumer; a mod that needs lit geometry has to arrive
 * through a layout with a normal element. Unit 0 is captured because it is the pair these layouts carry - a
 * {@code glMultiTexCoord2f(GL_TEXTURE0, ...)} is the same coordinate as {@code glTexCoord2f} - while a unit-1
 * coordinate is only counted, never written into that pair.
 */
public final class LegacyImmediateCapture {

    /** Where a completed run goes: the backend's immediate accept path, or a test's recorder. */
    public interface Sink {
        /**
         * Offers one run.
         *
         * @param address     the first vertex's address in this capture's buffer
         * @param stride      the vertex width, matching {@code format}
         * @param format      the immediate format id the run was packed for
         * @param vertexCount how many vertices the run holds
         * @param drawMode    the GL primitive the run was opened with
         * @return whether the run was taken; a refusal keeps the refusal reason at the caller
         */
        boolean submit(long address, int stride, int format, int vertexCount, int drawMode);
    }

    /**
     * Where a run drawn inside a display-list compile goes: it is not drawn now, it is copied and filed with the list.
     * <p>
     * The bytes are handed over as an address into this capture's own buffer, which the next {@code glBegin} reuses, so
     * the implementation must copy them before returning.
     */
    public interface CompiledRunSink {
        /**
         * Files one run with the list being compiled.
         *
         * @param format     the immediate format id the run was packed for
         * @param stride     the vertex width, matching {@code format}
         * @param count      how many vertices the run holds
         * @param drawMode   the GL primitive the run was opened with
         * @param address    the first vertex's address in this capture's buffer
         * @return whether the run was taken; a refusal keeps the reason at the caller
         */
        boolean record(int format, int stride, int count, int drawMode, long address);
    }

    /** Decides whether the renderer can draw a primitive at all; returns the reason when it cannot. */
    public interface ModeGate {
        String unsupportedReason(int drawMode);
    }

    /** {@code POSITION_COLOR}: position 3f and colour 4ub, the layout of an untextured immediate quad. */
    public static final int FORMAT_POSITION_COLOR = 1;
    /** {@code POSITION_TEX_COLOR}: the same plus a 2f texture coordinate. */
    public static final int FORMAT_POSITION_TEX_COLOR = 2;
    public static final int STRIDE_POSITION_COLOR = 16;
    public static final int STRIDE_POSITION_TEX_COLOR = 24;

    /**
     * The most vertices one run may hold. This is a guard, not a limit anyone reaches: the measured sessions' largest
     * {@code glBegin} run is four vertices, and the bound exists so a mod that never calls {@code glEnd} cannot grow the
     * buffer without limit.
     */
    public static final int MAX_VERTICES = 65536;

    /**
     * The GL primitive values a run is cut to whole primitives by; see {@link #wholePrimitives}.
     * <p>
     * Kept as the GL enum values instead of importing LWJGL so this class keeps no GL dependency - the reason its
     * packing rules can be checked by a plain JVM test.
     */
    private static final int GL_LINES = 0x0001;
    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_QUADS = 0x0007;
    private static final int GL_QUAD_STRIP = 0x0008;

    /** Room for {@link #MAX_VERTICES} textured vertices before the first growth; a run larger than this grows once. */
    private static final int INITIAL_BYTES = MAX_VERTICES * STRIDE_POSITION_TEX_COLOR;

    private ByteBuffer buffer;
    private long address;
    private int capacityBytes;

    private boolean active;
    private int mode;
    private int format = FORMAT_POSITION_TEX_COLOR;
    private int stride = STRIDE_POSITION_TEX_COLOR;
    private int count;
    /**
     * Whether the open run belongs to a display-list compile.
     * <p>
     * It changes where {@link #end(Sink, CompiledRunSink)} sends the run - to the list's copy instead of the drawing
     * path - and it is cleared with the run, so a run that is compiled and a run that is drawn cannot be confused.
     */
    private boolean compiling;

    private float texU;
    private float texV;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private float alpha = 1.0f;

    private long runs;
    private long submittedRuns;
    private long submittedVertices;
    private long compiledRuns;
    private long compiledVertices;
    private long modeRefusals;
    private long refusals;
    private long listRefusals;
    private long nestedRefusals;
    private long sinkRefusals;
    private long rectRefusals;
    private long otherTexUnitCoords;
    private long trailingVertices;
    private int lastOtherTexUnit = -1;
    private String lastRefusal = "";

    /** {@return whether a run is currently being collected}. */
    public boolean active() {
        return this.active;
    }

    /** {@return the primitive the open run was started with, or zero when none is open}. */
    public int mode() {
        return this.active ? this.mode : 0;
    }

    /** {@return the format id the open run is packed for}. */
    public int format() {
        return this.format;
    }

    /** {@return the vertex width the open run is packed for}. */
    public int stride() {
        return this.stride;
    }

    /** {@return how many vertices the open run holds}. */
    public int vertexCount() {
        return this.count;
    }

    /**
     * Opens a run.
     *
     * @param drawMode          the {@code glBegin} mode
     * @param textured          whether a texture was bound for this run, which selects the layout
     * @param compilingList     whether a display list is being compiled; its geometry belongs to the list recorder
     * @param gate              the renderer's answer to "can this primitive be drawn at all"
     * @return whether vertices will be collected
     */
    public boolean begin(int drawMode, boolean textured, boolean compilingList, ModeGate gate) {
        this.runs++;
        if (this.active) {
            // GL's own answer to glBegin inside glBegin is an error; the second run is the one that survives, which is
            // what keeps a mod that lost its glEnd from swallowing the rest of the frame.
            this.nestedRefusals++;
            this.active = false;
            this.count = 0;
        }
        this.mode = drawMode;
        this.count = 0;
        this.compiling = compilingList;
        String unsupported = gate == null ? null : gate.unsupportedReason(drawMode);
        if (unsupported != null) {
            // Asked before the compile branch so a list cannot file geometry the replay would have to refuse: the same
            // gate answers both, and a mode with no pipeline is a named loss either way.
            this.modeRefusals++;
            this.lastRefusal = unsupported;
            this.compiling = false;
            return false;
        }
        this.format = textured ? FORMAT_POSITION_TEX_COLOR : FORMAT_POSITION_COLOR;
        this.stride = textured ? STRIDE_POSITION_TEX_COLOR : STRIDE_POSITION_COLOR;
        this.active = true;
        return true;
    }

    /** {@code glVertex2f}: z is GL's current z, which a two-component vertex leaves at zero. */
    public void vertex2f(float x, float y) {
        vertex(x, y, 0.0f);
    }

    /** {@code glVertex2d}, narrowed to the float layout. */
    public void vertex2d(double x, double y) {
        vertex((float) x, (float) y, 0.0f);
    }

    /** {@code glVertex3f}. */
    public void vertex3f(float x, float y, float z) {
        vertex(x, y, z);
    }

    /** {@code glVertex3d}, narrowed to the float layout. */
    public void vertex3d(double x, double y, double z) {
        vertex((float) x, (float) y, (float) z);
    }

    /**
     * {@code glVertex2i}: GL rounds integer vertex components to float, which is what the layout carries.
     * <p>
     * The integer and short spellings were counted as unmapped and appended nothing, so a mod that drew a legacy
     * surface with them lost the whole run - the same class of silence the float and double spellings had before they
     * were routed. {@code glVertex2s} lands here too: a JVM local for a {@code (SS)V} descriptor is an int, and the
     * caller has already sign-extended it.
     */
    public void vertex2i(int x, int y) {
        vertex((float) x, (float) y, 0.0f);
    }

    /** {@code glVertex3i}, narrowed like {@link #vertex2i}. */
    public void vertex3i(int x, int y, int z) {
        vertex((float) x, (float) y, (float) z);
    }

    /** {@code glVertex4i}: the same w division the float spelling performs, on integer components. */
    public void vertex4i(int x, int y, int z, int w) {
        vertex4f((float) x, (float) y, (float) z, (float) w);
    }

    /** {@code glVertex4f}: the position is projected by {@code w}, as GL does for every vertex. */
    public void vertex4f(float x, float y, float z, float w) {
        if (w == 1.0f) {
            vertex(x, y, z);
            return;
        }
        if (w == 0.0f) {
            this.lastRefusal = "a vertex with w=0 has no finite position";
            this.refusals++;
            if (this.active) {
                this.active = false;
                this.count = 0;
            }
            return;
        }
        vertex(x / w, y / w, z / w);
    }

    /** {@code glVertex4d}, narrowed before the projection. */
    public void vertex4d(double x, double y, double z, double w) {
        vertex4f((float) x, (float) y, (float) z, (float) w);
    }

    /** {@code glTexCoord2f}: the coordinate every following vertex carries until it is set again. */
    public void texCoord(float u, float v) {
        this.texU = u;
        this.texV = v;
    }

    /**
     * {@code glTexCoord2i}/{@code glTexCoord2s}: narrowed to the float coordinate the capture stores, the same way
     * the double spelling is. A run whose UVs stayed at the previous value samples the wrong part of the atlas, which
     * is a wrong picture rather than a missing one - the reason to accept these spellings instead of counting them.
     */
    public void texCoord2i(int u, int v) {
        texCoord((float) u, (float) v);
    }

    /**
     * {@code glMultiTexCoord2f(GL_TEXTURE0, s, t)}: the unit-0 spelling of {@link #texCoord}.
     * <p>
     * GL keeps one current texture coordinate per unit and the immediate layouts here carry exactly one pair, which is
     * the pair the sampler reads - so unit 0 is the same coordinate and every other unit has no consumer. The caller
     * counts those other units instead of applying them: writing a unit-1 coordinate into the only pair would make a
     * mod's light-map coordinate its albedo coordinate, which is a wrong picture rather than a missing one.
     */
    public void texCoordUnit0(float u, float v) {
        texCoord(u, v);
    }

    /**
     * {@code glRectf}/{@code glRecti}/{@code glRectd}/{@code glRects}: GL defines the rectangle as one complete quad, not
     * as a vertex spelling, so it arrives as a whole run rather than as four calls the mod made.
     * <p>
     * The expansion is GL's own, in this order, and it leaves the current texture coordinate at {@code (0,1)} exactly as
     * the four {@code TexCoord2f} calls would: {@code (0,0)->(x1,y1)}, {@code (1,0)->(x2,y1)},
     * {@code (1,1)->(x2,y2)}, {@code (0,1)->(x1,y2)}. The colour is untouched. It is written through {@link #texCoord}
     * and {@link #vertex} so a two-component rectangle lands in whichever layout the run was opened for - textured
     * rectangles carry the sweep above, untextured ones ignore it.
     *
     * @return whether the four vertices were appended; {@code false} when no run is open, which is what a mod calling
     *         {@code glRect} between its own {@code glBegin}/{@code glEnd} gets (GL rejects that call outright)
     */
    public boolean rect(float x1, float y1, float x2, float y2) {
        if (!this.active) {
            this.rectRefusals++;
            this.lastRefusal = "glRect outside a run the capture opened";
            return false;
        }
        texCoord(0.0f, 0.0f);
        vertex(x1, y1, 0.0f);
        texCoord(1.0f, 0.0f);
        vertex(x2, y1, 0.0f);
        texCoord(1.0f, 1.0f);
        vertex(x2, y2, 0.0f);
        texCoord(0.0f, 1.0f);
        vertex(x1, y2, 0.0f);
        return true;
    }

    /** Records one multi-texture coordinate for a unit other than 0, which these layouts cannot carry. */
    public void noteOtherTexUnit(int unit) {
        this.otherTexUnitCoords++;
        this.lastOtherTexUnit = unit;
    }

    /** {@return how many multi-texture coordinates for a non-zero unit were counted instead of applied}. */
    public long otherTexUnitCoords() {
        return this.otherTexUnitCoords;
    }

    /** {@return the unit of the last non-zero multi-texture coordinate seen}. */
    public int lastOtherTexUnit() {
        return this.lastOtherTexUnit;
    }

    /** {@return how many {@code glRect} calls arrived with no run open to put them in}. */
    public long rectRefusals() {
        return this.rectRefusals;
    }

    /** {@code glColor4f}: the colour every following vertex carries until it is set again. */
    public void color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    /** The current colour's red component, for the {@code glGetFloatv(GL_CURRENT_COLOR)} mirror. */
    public float red() {
        return this.red;
    }

    public float green() {
        return this.green;
    }

    public float blue() {
        return this.blue;
    }

    public float alpha() {
        return this.alpha;
    }

    /**
     * Copies one collected vertex's position and texture coordinate out, for the bounded census of legacy runs.
     * <p>
     * A run's own numbers - mode, vertex count, format - say which primitive was written but not where it lands, and a
     * quad's placement is what separates "the menu's background image was never drawn" from "it was drawn off the
     * screen or with a zero extent". Reading the two end vertices is the smallest fact that answers that. It has to be
     * taken before {@link #end(Sink)}, which resets the buffer.
     *
     * @param index    the vertex to read, 0-based
     * @param position receives x, y and z
     * @param texCoord receives u and v, or 0,0 when the layout carries no texture coordinate
     * @return whether the index was inside the run
     */
    public boolean vertexAt(int index, float[] position, float[] texCoord) {
        if (index < 0 || index >= this.count) {
            return false;
        }
        long at = this.address + (long) index * this.stride;
        position[0] = MemoryUtil.memGetFloat(at);
        position[1] = MemoryUtil.memGetFloat(at + 4L);
        position[2] = MemoryUtil.memGetFloat(at + 8L);
        if (this.format == FORMAT_POSITION_TEX_COLOR) {
            texCoord[0] = MemoryUtil.memGetFloat(at + 12L);
            texCoord[1] = MemoryUtil.memGetFloat(at + 16L);
        } else {
            texCoord[0] = 0.0f;
            texCoord[1] = 0.0f;
        }
        return true;
    }

    /**
     * Closes the open run and offers it.
     *
     * @param sink where the run goes; a null sink refuses it
     * @return whether the run was submitted
     */
    public boolean end(Sink sink) {
        return end(sink, null);
    }

    /**
     * Closes the open run: a run drawn outside a list goes to {@code sink} and is drawn now, a run drawn inside a
     * {@code glNewList} compile goes to {@code compiledSink} as a copy the list replays.
     *
     * <p>GL's rule is the reason for the split: under {@code GL_COMPILE} the commands between {@code glNewList} and
     * {@code glEndList} are only stored, and drawing this run here would put the geometry on screen at the compile
     * matrix as well as at every replay. A compile whose record cannot be made is counted and named rather than drawn
     * - the one thing that must not happen is a run that appears neither in the list nor in the counters.
     *
     * @param sink         where a drawn run goes
     * @param compiledSink where a run being compiled into a list goes; {@code null} refuses it by name
     * @return whether the run was taken - submitted, or filed with the list
     */
    public boolean end(Sink sink, CompiledRunSink compiledSink) {
        if (!this.active) {
            return false;
        }
        this.active = false;
        boolean compiled = this.compiling;
        this.compiling = false;
        int vertices = this.count;
        this.count = 0;
        if (vertices <= 0) {
            // glBegin/glEnd around no vertices is not a draw in GL either.
            return false;
        }
        // GL draws the complete primitives of a run and ignores an incomplete trailing one (a fifth vertex in
        // GL_QUADS, the odd last vertex of GL_LINES). The renderer refuses a run whose count is not a whole number of
        // quads, so a mod that writes one extra vertex loses the whole run - the complete quad included. Cutting here
        // gives GL's answer and keeps the renderer's rule strict; the ignored vertices are counted so the loss is
        // visible rather than silent. A run with no complete primitive is offered unchanged: the capture does not
        // invent a draw, and the renderer's own refusal is the named answer.
        int drawable = wholePrimitives(vertices, this.mode);
        int offer = drawable > 0 ? drawable : vertices;
        if (drawable > 0 && drawable < vertices) {
            this.trailingVertices += vertices - drawable;
        }
        if (compiled) {
            if (compiledSink == null) {
                this.listRefusals++;
                this.refusals++;
                this.lastRefusal = "a display list is being compiled and no list record is available";
                return false;
            }
            if (!compiledSink.record(this.format, this.stride, offer, this.mode, this.address)) {
                // The sink owns the reason (out of memory, the module's byte bound); counted here as well so the
                // capture's own summary is not the quiet one.
                this.listRefusals++;
                this.refusals++;
                if (this.lastRefusal.isEmpty()) {
                    this.lastRefusal = "the display-list record refused it";
                }
                return false;
            }
            this.compiledRuns++;
            this.compiledVertices += offer;
            this.lastRefusal = "";
            return true;
        }
        if (sink == null) {
            this.refusals++;
            this.lastRefusal = "no immediate path is installed";
            return false;
        }
        boolean taken = sink.submit(this.address, this.stride, this.format, offer, this.mode);
        if (!taken) {
            this.sinkRefusals++;
            return false;
        }
        this.submittedRuns++;
        this.submittedVertices += offer;
        this.lastRefusal = "";
        return true;
    }

    /**
     * The longest prefix of a run that makes whole primitives.
     *
     * @param vertexCount how many vertices the run holds
     * @param drawMode    the {@code glBegin} mode
     * @return the vertices GL would actually draw; the remainder is an incomplete primitive, which GL ignores
     */
    static int wholePrimitives(int vertexCount, int drawMode) {
        switch (drawMode) {
            case GL_QUADS:
                return vertexCount - vertexCount % 4;
            case GL_QUAD_STRIP:
                // A strip needs four vertices for one quad; an odd trailing vertex is the incomplete primitive.
                return vertexCount < 4 ? 0 : vertexCount - vertexCount % 2;
            case GL_TRIANGLES:
                return vertexCount - vertexCount % 3;
            case GL_LINES:
                return vertexCount - vertexCount % 2;
            default:
                // Fans, strips and polygons draw any count of three or more; the renderer's own gate answers those.
                return vertexCount;
        }
    }

    /** Records a refusal the sink decided on, so the capture's counters and the caller's reason agree. */
    public void noteRefusal(String reason) {
        this.refusals++;
        this.lastRefusal = reason == null ? "" : reason;
    }

    /** {@return the run's first vertex address, for a caller that wants the sink's view of it}. */
    public long address() {
        return this.address;
    }

    /** Releases the vertex buffer; the capture may be used again afterwards, and grows a fresh one. */
    public void free() {
        this.active = false;
        this.count = 0;
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
            this.buffer = null;
            this.address = 0L;
            this.capacityBytes = 0;
        }
    }

    /** {@return a one-line census, in the shape the other route counters report}. */
    public String summary() {
        return "legacyImmediate runs=" + this.runs + " submitted=" + this.submittedRuns
                + " vertices=" + this.submittedVertices + " compiled=" + this.compiledRuns
                + " compiledVertices=" + this.compiledVertices + " modeRefusals=" + this.modeRefusals
                + " listRefusals=" + this.listRefusals + " nestedRefusals=" + this.nestedRefusals
                + " sinkRefusals=" + this.sinkRefusals + " rectRefusals=" + this.rectRefusals
                + " otherTexUnits=" + this.otherTexUnitCoords
                + " trailingVertices=" + this.trailingVertices
                + " refusals=" + this.refusals;
    }

    public long runs() {
        return this.runs;
    }

    public long submittedRuns() {
        return this.submittedRuns;
    }

    public long submittedVertices() {
        return this.submittedVertices;
    }

    public long modeRefusals() {
        return this.modeRefusals;
    }

    public long refusals() {
        return this.refusals;
    }

    public long listRefusals() {
        return this.listRefusals;
    }

    /** {@return how many runs were filed with a display-list compile rather than drawn}. */
    public long compiledRuns() {
        return this.compiledRuns;
    }

    /** {@return how many vertices those compiled runs hold, which is what the list's copies cost}. */
    public long compiledVertices() {
        return this.compiledVertices;
    }

    public long nestedRefusals() {
        return this.nestedRefusals;
    }

    public long sinkRefusals() {
        return this.sinkRefusals;
    }

    /** {@return vertices an incomplete trailing primitive left undrawn, the answer GL gives and counts here}. */
    public long trailingVertices() {
        return this.trailingVertices;
    }

    /** {@return the last named reason a run was not drawn}. */
    public String lastRefusal() {
        return this.lastRefusal;
    }

    // ---- the packing half ----

    private void vertex(float x, float y, float z) {
        if (!this.active) {
            return;
        }
        if (this.count >= MAX_VERTICES) {
            this.refusals++;
            this.lastRefusal = "more than " + MAX_VERTICES + " vertices in one glBegin";
            this.active = false;
            this.count = 0;
            return;
        }
        int width = this.stride;
        int needed = (this.count + 1) * width;
        if (this.buffer == null || this.capacityBytes < needed) {
            grow(needed);
        }
        long base = this.address + (long) this.count * width;
        MemoryUtil.memPutFloat(base, x);
        MemoryUtil.memPutFloat(base + 4L, y);
        MemoryUtil.memPutFloat(base + 8L, z);
        if (this.format == FORMAT_POSITION_TEX_COLOR) {
            MemoryUtil.memPutFloat(base + 12L, this.texU);
            MemoryUtil.memPutFloat(base + 16L, this.texV);
            colourAt(base + 20L);
        } else {
            colourAt(base + 12L);
        }
        this.count++;
    }

    private void colourAt(long address) {
        MemoryUtil.memPutByte(address, toByte(this.red));
        MemoryUtil.memPutByte(address + 1L, toByte(this.green));
        MemoryUtil.memPutByte(address + 2L, toByte(this.blue));
        MemoryUtil.memPutByte(address + 3L, toByte(this.alpha));
    }

    /** GL's {@code glColor4f} saturates before the conversion, and so does this. */
    private static byte toByte(float value) {
        float clamped = value <= 0.0f ? 0.0f : value >= 1.0f ? 1.0f : value;
        return (byte) (int) (clamped * 255.0f + 0.5f);
    }

    /**
     * Grows the vertex buffer, keeping what the open run has already written.
     * <p>
     * The initial capacity holds {@link #MAX_VERTICES} of the wider layout, so with the vertex bound in place this only
     * runs if one of the two constants changes; {@code nmemRealloc} keeps the guarantee honest rather than relying on
     * that arithmetic staying true.
     */
    private void grow(int needed) {
        int bytes = Math.max(needed, INITIAL_BYTES);
        if (this.buffer == null) {
            this.buffer = MemoryUtil.memAlloc(bytes);
        } else {
            long moved = MemoryUtil.nmemRealloc(this.address, bytes);
            this.buffer = MemoryUtil.memByteBuffer(moved, bytes);
        }
        this.address = MemoryUtil.memAddress(this.buffer);
        this.capacityBytes = bytes;
    }
}
