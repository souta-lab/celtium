package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Native run-frequency projection/fog packet. Instance records retain the individual model-view transform. */
final class ModelRunConstants {
    static final int BYTES = 96;

    private ModelRunConstants() { }

    /** Writes the complete push block and exposes exactly its 96-byte span to vkCmdPushConstants. */
    static void write(ByteBuffer target, float[] projection, float[] fog) {
        if (target.capacity() < BYTES || projection == null || projection.length < 16 || (fog != null && fog.length < 8)) {
            throw new IllegalArgumentException("incomplete model run constants");
        }
        target.clear().limit(BYTES);
        target.order(ByteOrder.nativeOrder());
        for (int i = 0; i < 16; i++) target.putFloat(i * 4, projection[i]);
        for (int i = 0; i < 4; i++) target.putFloat(64 + i * 4, fog == null ? 0.0f : fog[4 + i]);
        target.putFloat(80, fog == null ? 0.0f : fog[1]);
        target.putFloat(84, fog == null ? 0.0f : fog[2]);
        target.putFloat(88, fog == null ? 0.0f : fog[3]);
        target.putFloat(92, fog == null ? 0.0f : fog[0]);
    }
}
