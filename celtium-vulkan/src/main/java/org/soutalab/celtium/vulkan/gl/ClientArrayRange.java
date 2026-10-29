package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** Bounds an immediate draw by the original direct allocation, not a positioned view's cursor. */
public final class ClientArrayRange {
    private ClientArrayRange() {}

    public static long base(ByteBuffer buffer) {
        return MemoryUtil.memAddress(buffer, 0);
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
