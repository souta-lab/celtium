package org.soutalab.celtium.vulkan.compat;

import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;

import java.nio.ByteBuffer;

/**
 * The dynamic-light block of the Global Frame UBO: the reserved region the frame pipeline sets aside
 * for the shading-time light sources, written once at frame start by the {@link
 * DynamicLightExtension} and read by every terrain and gbuffers shader that wants them.
 * <p>
 * The layout is the one item 430 settled and the shipped terrain shaders already read, kept byte for
 * byte so the move into the frame block changes where the block lives, not what it says: a 16-byte
 * header - the source count as a float, then the camera position the sources were gathered around, as
 * provenance - followed by {@link DynamicLightService#MAX_LIGHTS} sources of one {@code vec4} each:
 * xyz the camera-relative position, w the light level on vanilla's 0..15 scale. Positions are
 * camera-relative because the shaders' world positions are too (they are built from region origins
 * written relative to the same camera); the header camera must therefore <em>not</em> be subtracted
 * again - item 430 measured exactly that double subtraction zeroing the raise everywhere.
 */
public final class FrameLights {

    /** Byte offset of the count (a float) inside the block. */
    public static final int COUNT_OFFSET = 0;
    /** Byte offset of the header camera position (three floats) inside the block. */
    public static final int CAMERA_OFFSET = 4;
    /** Byte offset of the first source; the header is 16 bytes. */
    public static final int SOURCES_OFFSET = 16;
    /** Bytes per source: one vec4. */
    public static final int SOURCE_BYTES = 16;
    /** The whole block: header plus every source slot, used or not. */
    public static final int BLOCK_BYTES = SOURCES_OFFSET + DynamicLightService.MAX_LIGHTS * SOURCE_BYTES;

    private FrameLights() {
    }

    /**
     * Writes one frame's gather into the block at {@code baseOffset}: the header, then the first
     * {@code count} sources out of {@code sources} (four floats each, the {@link DynamicLightService}
     * output shape). The count is clamped to the block's capacity - a service that over-reports loses
     * its tail here rather than writing into whatever the frame block keeps after this region.
     *
     * @return the number of sources actually written
     */
    public static int write(ByteBuffer block, int baseOffset, float[] sources, int count,
            double cameraX, double cameraY, double cameraZ) {
        int written = Math.max(0, Math.min(count, DynamicLightService.MAX_LIGHTS));
        block.putFloat(baseOffset + COUNT_OFFSET, (float) written);
        block.putFloat(baseOffset + CAMERA_OFFSET, (float) cameraX);
        block.putFloat(baseOffset + CAMERA_OFFSET + 4, (float) cameraY);
        block.putFloat(baseOffset + CAMERA_OFFSET + 8, (float) cameraZ);
        for (int i = 0; i < written; i++) {
            int at = baseOffset + SOURCES_OFFSET + i * SOURCE_BYTES;
            block.putFloat(at, sources[i * 4]);
            block.putFloat(at + 4, sources[i * 4 + 1]);
            block.putFloat(at + 8, sources[i * 4 + 2]);
            block.putFloat(at + 12, sources[i * 4 + 3]);
        }
        return written;
    }

    /** Empties the block: a count of zero, which every reader treats as "no sources this frame". */
    public static void clear(ByteBuffer block, int baseOffset) {
        block.putFloat(baseOffset + COUNT_OFFSET, 0.0F);
        block.putFloat(baseOffset + CAMERA_OFFSET, 0.0F);
        block.putFloat(baseOffset + CAMERA_OFFSET + 4, 0.0F);
        block.putFloat(baseOffset + CAMERA_OFFSET + 8, 0.0F);
    }
}
