package org.soutalab.celtium.core.geometry;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

/**
 * One general (non-axis-aligned) terrain quad in its 64-byte form (design V040-FINAL-PLAN
 * section 2.1): the fallback element for geometry the packed axis form cannot express. Four corner
 * positions as raw floats (section-local space), then the texture index, the packed light word, the
 * packed normal and a flags word.
 * <p>
 * The normal is packed 10/10/10 signed (two's complement per component, range -511..511 representing
 * -1..1) with the top 2 bits spare. Light is the same sky/block 4+4 packing the packed form uses,
 * widened: sky at bits 0..7 and block at bits 8..15 are the 0..15 levels scaled by 17, so a reader
 * that only knows the packed form's levels divides by 17.
 */
public final class GeneralQuad {

    public static final long BYTE_SIZE = 64L;

    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(12L, ValueLayout.JAVA_FLOAT).withName("positions"),
            ValueLayout.JAVA_INT.withName("textureIndex"),
            ValueLayout.JAVA_INT.withName("lightWord"),
            ValueLayout.JAVA_INT.withName("normalPacked"),
            ValueLayout.JAVA_INT.withName("flags"));

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

    /**
     * @param positions 12 floats: corner 0..3, each (x, y, z) in section-local space. Copied.
     */
    public GeneralQuad(float[] positions, int textureIndex, int lightWord, int normalPacked, int flags) {
        if (positions == null || positions.length != 12) {
            throw new IllegalArgumentException("positions must be exactly 12 floats (4 corners x xyz)");
        }
        this.positions = positions.clone();
        this.textureIndex = textureIndex;
        this.lightWord = lightWord;
        this.normalPacked = normalPacked;
        this.flags = flags;
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

    public void writeTo(MemorySegment segment, long offset) {
        MemorySegment.copy(positions, 0, segment, ValueLayout.JAVA_FLOAT, offset, 12);
        segment.set(ValueLayout.JAVA_INT, offset + 48L, textureIndex);
        segment.set(ValueLayout.JAVA_INT, offset + 52L, lightWord);
        segment.set(ValueLayout.JAVA_INT, offset + 56L, normalPacked);
        segment.set(ValueLayout.JAVA_INT, offset + 60L, flags);
    }

    public static GeneralQuad readFrom(MemorySegment segment, long offset) {
        float[] positions = new float[12];
        MemorySegment.copy(segment, ValueLayout.JAVA_FLOAT, offset, positions, 0, 12);
        return new GeneralQuad(positions,
                segment.get(ValueLayout.JAVA_INT, offset + 48L),
                segment.get(ValueLayout.JAVA_INT, offset + 52L),
                segment.get(ValueLayout.JAVA_INT, offset + 56L),
                segment.get(ValueLayout.JAVA_INT, offset + 60L));
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
                && Arrays.equals(positions, quad.positions);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(positions);
        result = 31 * result + textureIndex;
        result = 31 * result + lightWord;
        result = 31 * result + normalPacked;
        result = 31 * result + flags;
        return result;
    }
}
