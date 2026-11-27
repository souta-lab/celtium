package org.soutalab.celtium.glcompat.recording.commands;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLDebug;
import org.soutalab.celtium.glcompat.GLStateManager;
import org.soutalab.celtium.glcompat.GLTypes;
import org.soutalab.celtium.glcompat.states.PixelStoreState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.memFree;
import org.soutalab.celtium.glcompat.GLSMTextures;

/**
 * Command: glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels)
 * Updates a portion of an existing two-dimensional texture image during display list playback.
 *
 * <p>Pixel data is deep-copied off-heap at record time and the pixel-unpack state is
 * snapshotted alongside, so replay applies the same UNPACK_* parameters the recorder saw.
 */
public record TexSubImage2DCmd(
    int target,
    int level,
    int xoffset,
    int yoffset,
    int width,
    int height,
    int format,
    int type,
    @Nullable ByteBuffer pixels,
    @Nullable PixelStoreState unpack
) implements DisplayListCommand {

    public TexSubImage2DCmd {
        if (pixels == null) {
            unpack = null;
        }
    }

    public static TexSubImage2DCmd fromIntBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable IntBuffer pixels, PixelStoreState unpack) {
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexSubImage2DCmd fromFloatBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable FloatBuffer pixels, PixelStoreState unpack) {
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexSubImage2DCmd fromDoubleBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable DoubleBuffer pixels, PixelStoreState unpack) {
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexSubImage2DCmd fromByteBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable ByteBuffer pixels, PixelStoreState unpack) {
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    @Override
    public void execute() {
        if (pixels == null) {
            GLSMTextures.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, (ByteBuffer) null);
            return;
        }
        GLStateManager.forcePixelUnpackState(unpack);
        try {
            GLSMTextures.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        } finally {
            GLStateManager.restorePixelUnpackState(unpack);
        }
    }

    @Override
    public void delete() {
        if (pixels != null) {
            memFree(pixels);
        }
    }

    @Override
    public @NotNull String toString() {
        String dataStr = pixels != null ? (pixels.remaining() + " bytes") : "null";
        return String.format("TexSubImage2D(target=%s, level=%d, offset=(%d,%d), %dx%d, format=%s, type=%s, data=%s, unpack=%s)",
            GLDebug.getTextureTargetName(target), level, xoffset, yoffset, width, height,
            GLDebug.getTextureFormatName(format), GLTypes.name(type), dataStr, unpack);
    }
}
