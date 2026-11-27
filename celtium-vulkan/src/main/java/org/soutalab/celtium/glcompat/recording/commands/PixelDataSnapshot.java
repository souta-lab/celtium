package org.soutalab.celtium.glcompat.recording.commands;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.memAlloc;

/**
 * Record-time deep copy of client pixel data into off-heap memory, so a recorded
 * texture command does not depend on the caller's buffer staying alive or unmodified.
 */
final class PixelDataSnapshot {
    private PixelDataSnapshot() {}

    static @Nullable ByteBuffer copy(@Nullable ByteBuffer pixels) {
        if (pixels == null) return null;
        final int pos = pixels.position();
        final ByteBuffer out = memAlloc(pixels.remaining()).order(ByteOrder.nativeOrder());
        out.put(pixels);
        out.flip();
        pixels.position(pos);
        return out;
    }

    static @Nullable ByteBuffer copy(@Nullable IntBuffer pixels) {
        if (pixels == null) return null;
        final int pos = pixels.position();
        final ByteBuffer out = memAlloc(pixels.remaining() * 4).order(ByteOrder.nativeOrder());
        out.asIntBuffer().put(pixels);
        pixels.position(pos);
        return out;
    }

    static @Nullable ByteBuffer copy(@Nullable FloatBuffer pixels) {
        if (pixels == null) return null;
        final int pos = pixels.position();
        final ByteBuffer out = memAlloc(pixels.remaining() * 4).order(ByteOrder.nativeOrder());
        out.asFloatBuffer().put(pixels);
        pixels.position(pos);
        return out;
    }

    static @Nullable ByteBuffer copy(@Nullable DoubleBuffer pixels) {
        if (pixels == null) return null;
        final int pos = pixels.position();
        final ByteBuffer out = memAlloc(pixels.remaining() * 8).order(ByteOrder.nativeOrder());
        out.asDoubleBuffer().put(pixels);
        pixels.position(pos);
        return out;
    }
}
