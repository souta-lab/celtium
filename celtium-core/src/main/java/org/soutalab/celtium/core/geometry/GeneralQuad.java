package org.soutalab.celtium.core.geometry;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

/**
 * One general (non-axis-aligned) terrain quad in its 80-byte form (design V040-FINAL-PLAN
 * section 2.1, extended by the Neon UV ruling NEON-UV-RULING-2026-11-28): the fallback element
 * for geometry the packed axis form cannot express. Four corner positions as raw floats
 * (section-local space), then the texture index, the packed light word, the packed normal, a
 * flags word, and - offsets 64..79 - one packed UV word per corner.
 * <p>
 * The normal is packed 10/10/10 signed (two's complement per component, range -511..511 representing
 * -1..1) with the top 2 bits spare. Light is the same sky/block 4+4 packing the packed form uses,
 * widened: sky at bits 0..7 and block at bits 8..15 are the 0..15 levels scaled by 17, so a reader
 * that only knows the packed form's levels divides by 17.
 * <p>
 * Each corner's UV is sprite-local (normalised against the rectangle of the sprite
 * {@code textureIndex} names, so the contract stays independent of the physical atlas layout):
 * {@code u} in the low 16 bits and {@code v} in the high 16 bits, each a Q1.15 fixed-point value
 * ({@code round(local * 32768)}, clamped to {@code 0..32768}). Q1.15 represents 0.0, 1.0 and every
 * {@code k / 2^n} boundary bit-exactly, so 16x16 texel edges and CTM submap edges never drift.
 */
public final class GeneralQuad {

    public static final long BYTE_SIZE = 80L;

    /** Byte offset of the first per-corner packed UV word. */
    public static final long UV_PACKED_OFFSET = 64L;

    /**
     * The canonical full-sprite UV assignment in corner order - corner 0..3 map to
     * {@code (0,0), (0,1), (1,1), (1,0)} - used when a quad carries no UV of its own.
     */
    public static final int[] FULL_SPRITE_UV_PACKED = {
            packUv(0.0f, 0.0f), packUv(0.0f, 1.0f), packUv(1.0f, 1.0f), packUv(1.0f, 0.0f)};

    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(12L, ValueLayout.JAVA_FLOAT).withName("positions"),
            ValueLayout.JAVA_INT.withName("textureIndex"),
            ValueLayout.JAVA_INT.withName("lightWord"),
            ValueLayout.JAVA_INT.withName("normalPacked"),
            ValueLayout.JAVA_INT.withName("flags"),
            MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_INT).withName("uvPacked"));

    static {
        if (LAYOUT.byteSize() != BYTE_SIZE) {
            throw new ExceptionInInitializerError("GeneralQuad layout is " + LAYOUT.byteSize()
                    + " bytes, the contract says " + BYTE_SIZE);
        }
    }

    private final float[] positions;
    private final int textureIndex;
    private final int lightWord;
    private final int normalPacked;
    private final int flags;
    private final int[] uvPacked;

    /**
     * @param positions 12 floats: corner 0..3, each (x, y, z) in section-local space. Copied.
     *                  The quad's UVs default to {@link #FULL_SPRITE_UV_PACKED}.
     */
    public GeneralQuad(float[] positions, int textureIndex, int lightWord, int normalPacked, int flags) {
        this(positions, textureIndex, lightWord, normalPacked, flags, FULL_SPRITE_UV_PACKED);
    }

    /**
     * @param positions 12 floats: corner 0..3, each (x, y, z) in section-local space. Copied.
     * @param uvPacked  4 packed UV words, one per corner (see {@link #packUv}). Copied.
     */
    public GeneralQuad(float[] positions, int textureIndex, int lightWord, int normalPacked, int flags,
                       int[] uvPacked) {
        if (positions == null || positions.length != 12) {
            throw new IllegalArgumentException("positions must be exactly 12 floats (4 corners x xyz)");
        }
        if (uvPacked == null || uvPacked.length != 4) {
            throw new IllegalArgumentException("uvPacked must be exactly 4 words (one per corner)");
        }
        this.positions = positions.clone();
        this.textureIndex = textureIndex;
        this.lightWord = lightWord;
        this.normalPacked = normalPacked;
        this.flags = flags;
        this.uvPacked = uvPacked.clone();
    }

    /**
     * Packs one corner's sprite-local UV into a Q1.15 word: {@code u} in bits 0..15, {@code v} in
     * bits 16..31, each {@code clamp(round(local * 32768), 0, 32768)}. Non-finite inputs fall back
     * to 0 so a broken model can never poison the word's other half.
     */
    public static int packUv(float localU, float localV) {
        return (quantizeUv(localU) & 0xFFFF) | ((quantizeUv(localV) & 0xFFFF) << 16);
    }

    private static int quantizeUv(float local) {
        if (!Float.isFinite(local)) {
            return 0;
        }
        int q = Math.round(local * 32768.0f);
        if (q < 0) {
            return 0;
        }
        return Math.min(q, 32768);
    }

    /** The {@code u} of a packed UV word, back in 0..1 sprite-local space. */
    public static float unpackUvU(int packed) {
        return (packed & 0xFFFF) / 32768.0f;
    }

    /** The {@code v} of a packed UV word, back in 0..1 sprite-local space. */
    public static float unpackUvV(int packed) {
        return ((packed >>> 16) & 0xFFFF) / 32768.0f;
    }

    /** Packs a unit normal into the 10/10/10 signed form {@link #normalPacked()} carries. */
    public static int packNormal(float x, float y, float z) {
        return packNormalComponent(x) | (packNormalComponent(y) << 10) | (packNormalComponent(z) << 20);
    }

    private static int packNormalComponent(float value) {
        int scaled = Math.round(value * 511.0f);
        if (scaled < -511 || scaled > 511) {
            throw new IllegalArgumentException("normal component " + value + " is outside -1..1");
        }
        return scaled & 0x3FF;
    }

    public static float unpackNormalComponent(int packed, int shift) {
        int raw = (packed >>> shift) & 0x3FF;
        int signed = raw >= 512 ? raw - 1024 : raw;
        return signed / 511.0f;
    }

    /** The 12 corner floats (a copy; the quad stays immutable). */
    public float[] positions() {
        return positions.clone();
    }

    public float cornerX(int corner) {
        return positions[corner * 3];
    }

    public float cornerY(int corner) {
        return positions[corner * 3 + 1];
    }

    public float cornerZ(int corner) {
        return positions[corner * 3 + 2];
    }

    public int textureIndex() {
        return textureIndex;
    }

    public int lightWord() {
        return lightWord;
    }

    public int normalPacked() {
        return normalPacked;
    }

    public int flags() {
        return flags;
    }

    /** The 4 packed UV words, one per corner (a copy; the quad stays immutable). */
    public int[] uvPacked() {
        return uvPacked.clone();
    }

    /** The packed UV word of one corner. */
    public int uvPacked(int corner) {
        return uvPacked[corner];
    }

    public void writeTo(MemorySegment segment, long offset) {
        MemorySegment.copy(positions, 0, segment, ValueLayout.JAVA_FLOAT, offset, 12);
        segment.set(ValueLayout.JAVA_INT, offset + 48L, textureIndex);
        segment.set(ValueLayout.JAVA_INT, offset + 52L, lightWord);
        segment.set(ValueLayout.JAVA_INT, offset + 56L, normalPacked);
        segment.set(ValueLayout.JAVA_INT, offset + 60L, flags);
        MemorySegment.copy(uvPacked, 0, segment, ValueLayout.JAVA_INT, offset + UV_PACKED_OFFSET, 4);
    }

    public static GeneralQuad readFrom(MemorySegment segment, long offset) {
        float[] positions = new float[12];
        MemorySegment.copy(segment, ValueLayout.JAVA_FLOAT, offset, positions, 0, 12);
        int[] uvPacked = new int[4];
        MemorySegment.copy(segment, ValueLayout.JAVA_INT, offset + UV_PACKED_OFFSET, uvPacked, 0, 4);
        return new GeneralQuad(positions,
                segment.get(ValueLayout.JAVA_INT, offset + 48L),
                segment.get(ValueLayout.JAVA_INT, offset + 52L),
                segment.get(ValueLayout.JAVA_INT, offset + 56L),
                segment.get(ValueLayout.JAVA_INT, offset + 60L),
                uvPacked);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeneralQuad quad)) {
            return false;
        }
        return textureIndex == quad.textureIndex && lightWord == quad.lightWord
                && normalPacked == quad.normalPacked && flags == quad.flags
                && Arrays.equals(positions, quad.positions)
                && Arrays.equals(uvPacked, quad.uvPacked);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(positions);
        result = 31 * result + textureIndex;
        result = 31 * result + lightWord;
        result = 31 * result + normalPacked;
        result = 31 * result + flags;
        result = 31 * result + Arrays.hashCode(uvPacked);
        return result;
    }
}
