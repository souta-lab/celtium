package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.system.MemoryUtil.memAddress;

/**
 * GLSM stage 3, census S-23 (superset-proof residual M-2): the single owner of the
 * {@code glGetString} / {@code glGetStringi} intern buffer - the one native buffer every
 * string answer is written into before its address is handed to the guest.
 * <p>
 * The semantics are the stub's, moved unchanged: one shared buffer is enough because the
 * game compares or prints the string, it does not keep the pointer. The buffer is direct,
 * holds the ASCII bytes plus a NUL terminator, and grows only when a string larger than
 * what is already interned arrives; the returned address therefore changes only on a
 * grow, exactly as when the buffer lived in {@code CeltiumGlStub}'s fields. The answers
 * themselves (which string each pname gets) stay in {@code GlStateQueries}; this class
 * owns only the storage the answers are delivered through, so a string answer and the
 * buffer it was read from can no longer belong to different records.
 * <p>
 * The previous owner was the installed stub instance's {@code getStringBuffer} /
 * {@code getStringAddress} field pair. Only one stub is ever installed as the function
 * provider, so the per-instance buffer and this JVM-singleton buffer are the same single
 * buffer in every run that exists.
 */
public final class GlsmStringState {
    private static final GlsmStringState CURRENT = new GlsmStringState();

    public static GlsmStringState current() { return CURRENT; }

    private ByteBuffer buffer;
    private long address;

    private GlsmStringState() {
    }

    /**
     * Writes {@code ascii} (plus a NUL terminator) into the intern buffer and returns the
     * buffer's native address - the pointer {@code glGetString} hands back.
     */
    public long intern(String ascii) {
        // One buffer is enough: the game compares/prints, it does not keep the pointer. Grow it only when the
        // string is larger than what is already interned.
        byte[] bytes = (ascii + "\0").getBytes(StandardCharsets.US_ASCII);
        if (this.buffer == null || this.buffer.capacity() < bytes.length) {
            this.buffer = ByteBuffer.allocateDirect(bytes.length);
            this.address = memAddress(this.buffer);
        }
        this.buffer.clear();
        this.buffer.put(bytes);
        return this.address;
    }

    /** The current intern buffer's capacity in bytes, or 0 before the first intern. */
    public int capacity() { return this.buffer == null ? 0 : this.buffer.capacity(); }
}
