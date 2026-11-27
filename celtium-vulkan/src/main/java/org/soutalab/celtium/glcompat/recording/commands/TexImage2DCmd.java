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
 * Command: glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels)
 * Specifies a two-dimensional texture image during display list playback.
 *
 * <p>Pixel data is deep-copied off-heap at record time and the pixel-unpack state is
 * snapshotted alongside, so replay applies the same UNPACK_* parameters the recorder saw.
 */
public record TexImage2DCmd(
    int target,
    int level,
    int internalformat,
    int width,
    int height,
    int border,
    int format,
    int type,
    @Nullable ByteBuffer pixels,
    @Nullable PixelStoreState unpack
) implements DisplayListCommand {

    public TexImage2DCmd {
        if (pixels == null) {
            unpack = null;
        }
    }

    public static TexImage2DCmd fromIntBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable IntBuffer pixels, PixelStoreState unpack) {
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexImage2DCmd fromFloatBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable FloatBuffer pixels, PixelStoreState unpack) {
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexImage2DCmd fromDoubleBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable DoubleBuffer pixels, PixelStoreState unpack) {
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexImage2DCmd fromByteBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels, PixelStoreState unpack) {
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    @Override
    public void execute() {
        if (pixels == null) {
            GLSMTextures.glTexImage2D(target, level, internalformat, width, height, border, format, type, (ByteBuffer) null);
            return;
        }
        GLStateManager.forcePixelUnpackState(unpack);
        try {
            GLSMTextures.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
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
        return String.format("TexImage2D(target=%s, level=%d, internalformat=%s, %dx%d, border=%d, format=%s, type=%s, data=%s, unpack=%s)",
            GLDebug.getTextureTargetName(target), level, GLDebug.getTextureFormatName(internalformat),
            width, height, border, GLDebug.getTextureFormatName(format), GLTypes.name(type), dataStr, unpack);
    }
}
