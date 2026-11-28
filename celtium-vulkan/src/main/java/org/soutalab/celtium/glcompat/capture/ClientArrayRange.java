package org.soutalab.celtium.glcompat.capture;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** Bounds an immediate draw by the original direct allocation, not a positioned view's cursor. */
public final class ClientArrayRange {
    private ClientArrayRange() {}

    public static long base(ByteBuffer buffer) {
        return MemoryUtil.memAddress(buffer, 0);
    }

    // The owned-buffer publication seat (moved from the retired monolith in E4-b2): the game's
    // BufferBuilder range for the client-array draw that is about to be made from it, published by
    // ClientArrayBufferOwnershipMixin at the draw call site and consumed once by the draw that follows.
    private static final ThreadLocal<long[]> PUBLISHED = ThreadLocal.withInitial(() -> new long[3]);
    private static volatile long decodedDraws;

    /** Publishes the game's own buffer range; a zero address or a zero length is ignored. */
    public static void publish(long address, long bytes) {
        if (address == 0L || bytes <= 0L) {
            return;
        }
        long[] slot = PUBLISHED.get();
        slot[0] = address;
        slot[1] = bytes;
        slot[2] = 1L;
    }

    /** {@return the published owned buffer as {address, bytes} once, or null when none is pending}. */
    public static long[] consumePublished() {
        long[] slot = PUBLISHED.get();
        if (slot[2] == 0L) {
            return null;
        }
        slot[2] = 0L;
        return slot;
    }

    /** Counts one draw decoded from the owned-buffer publication, for the mixin's own census. */
    public static void noteDecoded() {
        decodedDraws++;
    }

    /** {@return how many draws the owned-buffer publication decoded}. */
    public static long decodedDraws() {
        return decodedDraws;
    }

    public static boolean covers(long base, long capacity, long pointer, long bytes) {
        if (base <= 0 || capacity <= 0 || bytes < 0 || pointer < base) {
            return false;
        }
        // Subtraction rather than pointer + bytes prevents signed-address overflow.
        long displacement = pointer - base;
        return displacement >= 0 && displacement <= capacity && bytes <= capacity - displacement;
    }
}
