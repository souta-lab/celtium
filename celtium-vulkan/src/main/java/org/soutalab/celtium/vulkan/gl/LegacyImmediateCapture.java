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
 * layout the renderer has no draw for (the gate), geometry inside a display-list compile, more than
 * {@link #MAX_VERTICES} vertices in one run, and a sink that will not take the run all leave the run undrawn and land
 * in a named counter. Under GL-less a refusal draws nothing, so the count is the only way the loss is visible.</li>
 * </ul>
 * <p>
 * Not captured, deliberately: normals and multi-texture coordinates. Every mode here is drawn through an unlit
 * immediate layout, so a normal would have no consumer; a mod that needs lit geometry has to arrive through a layout
 * with a normal element.
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

    private float texU;
    private float texV;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private float alpha = 1.0f;

    private long runs;
    private long submittedRuns;
    private long submittedVertices;
    private long modeRefusals;
    private long refusals;
    private long listRefusals;
    private long nestedRefusals;
    private long sinkRefusals;
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
        if (compilingList) {
            // A list's vertices are captured by the model path, which replays them with the list. Drawing them here
            // would put the geometry on screen at compile time as well.
            this.listRefusals++;
            this.lastRefusal = "a display list is being compiled";
            return false;
        }
        String unsupported = gate == null ? null : gate.unsupportedReason(drawMode);
        if (unsupported != null) {
            this.modeRefusals++;
            this.lastRefusal = unsupported;
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
     * Closes the open run and offers it.
     *
     * @param sink where the run goes; a null sink refuses it
     * @return whether the run was submitted
     */
    public boolean end(Sink sink) {
        if (!this.active) {
            return false;
        }
        this.active = false;
        int vertices = this.count;
        this.count = 0;
        if (vertices <= 0) {
            // glBegin/glEnd around no vertices is not a draw in GL either.
            return false;
        }
        if (sink == null) {
            this.refusals++;
            this.lastRefusal = "no immediate path is installed";
            return false;
        }
        boolean taken = sink.submit(this.address, this.stride, this.format, vertices, this.mode);
        if (!taken) {
            this.sinkRefusals++;
            return false;
        }
        this.submittedRuns++;
        this.submittedVertices += vertices;
        this.lastRefusal = "";
        return true;
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
                + " vertices=" + this.submittedVertices + " modeRefusals=" + this.modeRefusals
                + " listRefusals=" + this.listRefusals + " nestedRefusals=" + this.nestedRefusals
                + " sinkRefusals=" + this.sinkRefusals + " refusals=" + this.refusals;
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

    public long nestedRefusals() {
        return this.nestedRefusals;
    }

    public long sinkRefusals() {
        return this.sinkRefusals;
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
