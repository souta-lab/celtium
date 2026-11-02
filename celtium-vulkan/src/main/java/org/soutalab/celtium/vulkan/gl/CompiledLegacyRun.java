package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryUtil;

/**
 * One {@code glBegin}/{@code glEnd} run that was drawn inside a {@code glNewList} compile, copied out so the list owns
 * it and replaying it does not read the capture's reused buffer.
 *
 * <p>Why a copy is required rather than a reference. {@link LegacyImmediateCapture} keeps one vertex buffer for the
 * whole run of the frame - the next {@code glBegin} starts writing at its first byte - so a list that remembered the
 * address would replay whatever the last immediate run of the frame happened to leave there. GL's own rule is the
 * same: the commands between {@code glNewList} and {@code glEndList} are compiled, and the data they read is copied at
 * compile time.
 *
 * <p>What is deliberately not stored: the matrix, the texture binding, the blend state. A display list does not bake
 * those in either - the recorded commands change them in order at replay - so this object carries only the vertices and
 * the primitive, and the replay submits it through the same immediate path with whatever state is current at
 * {@code glCallList}. That is what makes a marker compiled once and called under a different {@code glTranslate} land
 * where the mod asked for it.
 *
 * <p>The same rule applies to the attributes that <em>are</em> per vertex here. GL compiles the commands, not the
 * values: a vertex written inside a list takes the colour and texture coordinate current at {@code glCallList} unless
 * the list itself sets them. The capture therefore marks, per run, which attributes the list never set
 * ({@link #ATTRIBUTE_COLOUR_FROM_CALL}, {@link #ATTRIBUTE_TEX_FROM_CALL}) and {@link #applyCallState} writes the
 * call-time value into the copy before the submit. Baking the compile-time value instead is a different picture, not a
 * smaller one - the round that added display-list replay shipped that mistake, and the fixture that asserted it (a green
 * quad drawn under a red call) was asserting a colour GL never draws.
 *
 * <p>The bytes cost one copy per run: GregTech's rotation marker is 121 vertices of a 16-byte untextured layout, about
 * 2 KB, and the caller bounds the total it will hold.
 */
public final class CompiledLegacyRun {

    /**
     * The marker put in a display list's command array in front of the run, so the replay can tell this entry from a
     * recorded GL command without a cast that would throw.
     */
    public static final Object TAG = new Object();

    /** The copied vertices, owned here until {@link #free()}. */
    private final ByteBuffer bytes;
    /** The copied vertices' address, kept because it is what the immediate path takes. */
    public final long address;
    /** The vertex width, matching {@link #format}. */
    public final int stride;
    /** The immediate layout id the vertices were packed for. */
    public final int format;
    /** How many vertices the run holds. */
    public final int count;
    /** The GL primitive the run was opened with. */
    public final int mode;
    /** Which attributes the replay has to take from the state current at {@code glCallList}; see the ATTRIBUTE_* masks. */
    private final int attributes;
    /** How many replays substituted the call-time colour, and how many the call-time coordinate, into the copy. */
    private int callTimeColourReplays;
    private int callTimeTexReplays;

    /**
     * The colour was not set by any command inside the compile, so GL draws these vertices with the current colour at
     * {@code glCallList}. The replay writes that colour into the copy before submitting it.
     */
    public static final int ATTRIBUTE_COLOUR_FROM_CALL = 1;
    /** The same rule for the texture coordinate; only a {@link LegacyImmediateCapture#FORMAT_POSITION_TEX_COLOR} run. */
    public static final int ATTRIBUTE_TEX_FROM_CALL = 2;

    private CompiledLegacyRun(ByteBuffer bytes, int format, int stride, int count, int mode, int attributes) {
        this.bytes = bytes;
        this.address = MemoryUtil.memAddress(bytes);
        this.stride = stride;
        this.format = format;
        this.count = count;
        this.mode = mode;
        this.attributes = attributes;
    }

    /**
     * Copies one run's vertices out of the capture's buffer.
     *
     * @param format     the immediate layout id
     * @param stride     the vertex width in bytes
     * @param count      how many vertices to copy
     * @param mode       the GL primitive
     * @param address    the first vertex in the capture's buffer
     * @param attributes the {@code ATTRIBUTE_*} flags the capture decided for this run
     * @return the copy, or {@code null} when the size is not payable (a caller's bad count is not worth an error)
     */
    public static CompiledLegacyRun copy(int format, int stride, int count, int mode, long address, int attributes) {
        if (count <= 0 || stride <= 0 || address == 0L) {
            return null;
        }
        long size = (long) count * (long) stride;
        if (size > Integer.MAX_VALUE) {
            return null;
        }
        ByteBuffer copy;
        try {
            copy = MemoryUtil.memAlloc((int) size);
        } catch (RuntimeException | OutOfMemoryError refused) {
            return null;
        }
        MemoryUtil.memCopy(address, MemoryUtil.memAddress(copy), size);
        return new CompiledLegacyRun(copy, format, stride, count, mode, attributes);
    }

    /** {@return the bytes this run holds}, for the owner's byte bound. */
    public long bytes() {
        return (long) this.count * (long) this.stride;
    }

    /** {@return the attribute-provenance flags the capture recorded for this run}. */
    public int attributes() {
        return this.attributes;
    }

    /**
     * Substitutes the state current at {@code glCallList} into the attributes the list never set.
     *
     * <p>This is GL's rule for a display list, and the reason it is applied here rather than at compile time: the list
     * compiles the commands, so a {@code glVertex} inside it takes whatever colour and texture coordinate are current
     * when the list is <em>executed</em> - unless the list itself carries a {@code glColor}/{@code glTexCoord} command,
     * in which case the replay applies that command first and the baked bytes already agree with it. A mod that
     * compiles a mesh once and draws it in a different colour per instance (and OpenComputers' screen text, AE-Net-Vis'
     * link renderers and GregTech's marker all set the colour before {@code glCallList}) relies on exactly this.
     *
     * <p>The substitution is idempotent and may happen many times per frame: the copy is this list's alone, and every
     * replay overwrites the same bytes with the values current then.
     *
     * @param red   the current red at {@code glCallList}
     * @param green the current green
     * @param blue  the current blue
     * @param alpha the current alpha
     * @param u     the current texture coordinate's s
     * @param v     the current texture coordinate's t
     * @return whether anything was written
     */
    public boolean applyCallState(float red, float green, float blue, float alpha, float u, float v) {
        boolean textured = this.format == LegacyImmediateCapture.FORMAT_POSITION_TEX_COLOR;
        boolean colour = (this.attributes & ATTRIBUTE_COLOUR_FROM_CALL) != 0;
        boolean uv = textured && (this.attributes & ATTRIBUTE_TEX_FROM_CALL) != 0;
        if (!colour && !uv) {
            return false;
        }
        if (!textured && this.format != LegacyImmediateCapture.FORMAT_POSITION_COLOR) {
            // A layout this class does not know the offsets of: writing at 12 would corrupt the position.
            return false;
        }
        byte r = LegacyImmediateCapture.colourByte(red);
        byte g = LegacyImmediateCapture.colourByte(green);
        byte b = LegacyImmediateCapture.colourByte(blue);
        byte a = LegacyImmediateCapture.colourByte(alpha);
        long base = this.address;
        long stride = this.stride;
        for (int vertex = 0; vertex < this.count; vertex++) {
            long at = base + (long) vertex * stride;
            if (uv) {
                MemoryUtil.memPutFloat(at + 12L, u);
                MemoryUtil.memPutFloat(at + 16L, v);
            }
            if (colour) {
                long colourAt = textured ? at + 20L : at + 12L;
                MemoryUtil.memPutByte(colourAt, r);
                MemoryUtil.memPutByte(colourAt + 1L, g);
                MemoryUtil.memPutByte(colourAt + 2L, b);
                MemoryUtil.memPutByte(colourAt + 3L, a);
            }
        }
        if (colour) {
            this.callTimeColourReplays++;
        }
        if (uv) {
            this.callTimeTexReplays++;
        }
        return true;
    }

    /** {@return how many replays have written the call-time colour into this run}. */
    public int callTimeColourReplays() {
        return this.callTimeColourReplays;
    }

    /** {@return how many replays have written the call-time texture coordinate into this run}. */
    public int callTimeTexReplays() {
        return this.callTimeTexReplays;
    }

    /**
     * {@return the owned buffer}, so the list that holds this run registers it with the payload list that frees a
     * replaced or deleted list's copies through the one path that already owns every other compile-time buffer.
     */
    public ByteBuffer buffer() {
        return this.bytes;
    }

    /**
     * Releases the copy.
     *
     * <p>Called only when the list that owns this run is replaced or deleted, which is also the only moment nothing can
     * replay it again: a freed address would otherwise be read by the next {@code glCallList}.
     */
    public void free() {
        MemoryUtil.memFree(this.bytes);
    }

    /** {@return the run as one line, for a bounded diagnostic}. */
    public String describe() {
        return "mode=" + this.mode + " vertices=" + this.count + " format=" + this.format + " stride=" + this.stride
                + " attributes=" + this.attributes;
    }
}
