package org.soutalab.celtium.core.geometry;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * One axis-aligned terrain quad in its packed 16-byte form (design V040-FINAL-PLAN section 2.1/2.2):
 * the mesher's output and the terrain slab's element type. Pack-OFF frames carry nothing but these;
 * a pack-ON section adds a separate auxiliary column ({@code +2 B mc_Entity} per quad, see
 * {@link SectionDesc#mcEntityBase()}), never a wider element here.
 * <p>
 * Bit layout, four 32-bit words:
 * <ul>
 *   <li>word 0: block-local min corner x/y/z at 6 bits each (the 18&sup3; meshing snapshot fits, with
 *       headroom), then the axis (0=X, 1=Y, 2=Z) at 2 bits and the facing sign at 1 bit.</li>
 *   <li>word 1: U/V extents at 6 bits each, stored as extent-1 (a quad spans 1..64 blocks), then the
 *       texture index at 20 bits (an index into the single bindless texture table).</li>
 *   <li>word 2: the texture cell origin U/V at 8 bits each, sky light and block light at 4 bits each,
 *       then the fixed-point shade byte.</li>
 *   <li>word 3: the tint R/G/B bytes, then a flags byte (bit 0: the tint is present).</li>
 * </ul>
 * The layout is a contract: later stages generate and consume exactly these bits, so the field
 * boundaries are pinned by the layout contract test, not by convention.
 */
public record PackedAxisQuad(int word0, int word1, int word2, int word3) {

    public static final long BYTE_SIZE = 16L;

    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("word0"),
            ValueLayout.JAVA_INT.withName("word1"),
            ValueLayout.JAVA_INT.withName("word2"),
            ValueLayout.JAVA_INT.withName("word3"));

    public static final int FLAG_TINT_PRESENT = 0x1;

    static {
        if (LAYOUT.byteSize() != BYTE_SIZE) {
            throw new ExceptionInInitializerError("PackedAxisQuad layout is " + LAYOUT.byteSize()
                    + " bytes, the contract says " + BYTE_SIZE);
        }
    }

    /** Builds a quad from its decoded fields, range-checking every one against its bit width. */
    public static PackedAxisQuad of(int minX, int minY, int minZ, int axis, boolean negativeFacing,
                                    int extentU, int extentV, int textureIndex,
                                    int cellU, int cellV, int skyLight, int blockLight, int shade,
                                    int tintR, int tintG, int tintB, int flags) {
        requireRange("minX", minX, 0, 63);
        requireRange("minY", minY, 0, 63);
        requireRange("minZ", minZ, 0, 63);
        requireRange("axis", axis, 0, 2);
        requireRange("extentU", extentU, 1, 64);
        requireRange("extentV", extentV, 1, 64);
        requireRange("textureIndex", textureIndex, 0, (1 << 20) - 1);
        requireRange("cellU", cellU, 0, 255);
        requireRange("cellV", cellV, 0, 255);
        requireRange("skyLight", skyLight, 0, 15);
        requireRange("blockLight", blockLight, 0, 15);
        requireRange("shade", shade, 0, 255);
        requireRange("tintR", tintR, 0, 255);
        requireRange("tintG", tintG, 0, 255);
        requireRange("tintB", tintB, 0, 255);
        requireRange("flags", flags, 0, 255);
        int w0 = minX | (minY << 6) | (minZ << 12) | (axis << 18) | ((negativeFacing ? 1 : 0) << 20);
        int w1 = (extentU - 1) | ((extentV - 1) << 6) | (textureIndex << 12);
        int w2 = cellU | (cellV << 8) | (skyLight << 16) | (blockLight << 20) | (shade << 24);
        int w3 = tintR | (tintG << 8) | (tintB << 16) | (flags << 24);
        return new PackedAxisQuad(w0, w1, w2, w3);
    }

    private static void requireRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " = " + value + " is outside " + min + ".." + max);
        }
    }

    public int minX() {
        return word0 & 0x3F;
    }

    public int minY() {
        return (word0 >>> 6) & 0x3F;
    }

    public int minZ() {
        return (word0 >>> 12) & 0x3F;
    }

    /** 0 = X-facing plane, 1 = Y, 2 = Z. */
    public int axis() {
        return (word0 >>> 18) & 0x3;
    }

    public boolean negativeFacing() {
        return ((word0 >>> 20) & 0x1) != 0;
    }

    /** Quad extent along the face's U axis, in blocks (1..64). */
    public int extentU() {
        return (word1 & 0x3F) + 1;
    }

    /** Quad extent along the face's V axis, in blocks (1..64). */
    public int extentV() {
        return ((word1 >>> 6) & 0x3F) + 1;
    }

    public int textureIndex() {
        return word1 >>> 12;
    }

    public int cellU() {
        return word2 & 0xFF;
    }

    public int cellV() {
        return (word2 >>> 8) & 0xFF;
    }

    public int skyLight() {
        return (word2 >>> 16) & 0xF;
    }

    public int blockLight() {
        return (word2 >>> 20) & 0xF;
    }

    /** Fixed-point shade (0..255) the mesher baked for this face. */
    public int shade() {
        return (word2 >>> 24) & 0xFF;
    }

    public int tintR() {
        return word3 & 0xFF;
    }

    public int tintG() {
        return (word3 >>> 8) & 0xFF;
    }

    public int tintB() {
        return (word3 >>> 16) & 0xFF;
    }

    public int flags() {
        return (word3 >>> 24) & 0xFF;
    }

    public boolean hasTint() {
        return (flags() & FLAG_TINT_PRESENT) != 0;
    }

    /** Writes this quad at {@code offset} in a segment laid out as {@link #LAYOUT} repeats. */
    public void writeTo(MemorySegment segment, long offset) {
        segment.set(ValueLayout.JAVA_INT, offset, word0);
        segment.set(ValueLayout.JAVA_INT, offset + 4L, word1);
        segment.set(ValueLayout.JAVA_INT, offset + 8L, word2);
        segment.set(ValueLayout.JAVA_INT, offset + 12L, word3);
    }

    public static PackedAxisQuad readFrom(MemorySegment segment, long offset) {
        return new PackedAxisQuad(
                segment.get(ValueLayout.JAVA_INT, offset),
                segment.get(ValueLayout.JAVA_INT, offset + 4L),
                segment.get(ValueLayout.JAVA_INT, offset + 8L),
                segment.get(ValueLayout.JAVA_INT, offset + 12L));
    }
}
