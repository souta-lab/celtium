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

    private CompiledLegacyRun(ByteBuffer bytes, int format, int stride, int count, int mode) {
        this.bytes = bytes;
        this.address = MemoryUtil.memAddress(bytes);
        this.stride = stride;
        this.format = format;
        this.count = count;
        this.mode = mode;
    }

    /**
     * Copies one run's vertices out of the capture's buffer.
     *
     * @param format  the immediate layout id
     * @param stride  the vertex width in bytes
     * @param count   how many vertices to copy
     * @param mode    the GL primitive
     * @param address the first vertex in the capture's buffer
     * @return the copy, or {@code null} when the size is not payable (a caller's bad count is not worth an error)
     */
    public static CompiledLegacyRun copy(int format, int stride, int count, int mode, long address) {
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
        return new CompiledLegacyRun(copy, format, stride, count, mode);
    }

    /** {@return the bytes this run holds}, for the owner's byte bound. */
    public long bytes() {
        return (long) this.count * (long) this.stride;
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
        return "mode=" + this.mode + " vertices=" + this.count + " format=" + this.format + " stride=" + this.stride;
    }
}
