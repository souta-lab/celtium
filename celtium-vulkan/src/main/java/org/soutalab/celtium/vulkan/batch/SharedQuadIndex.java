package org.soutalab.celtium.vulkan.batch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The one shared static index buffer every immediate quad draw uses (new-architecture design
 * section 2.2). The pattern for quad {@code i} is {@code 4i, 4i+1, 4i+2, 4i, 4i+2, 4i+3}, generated
 * once at startup; per-frame CPU index expansion (the old {@code VulkanImmediateDrawer}'s
 * {@code expandIndices}) and per-draw index uploads disappear. A draw addresses its slice of the
 * pattern through {@code firstIndex = firstQuad * 6} plus a {@code vertexOffset}, exactly as
 * {@link ImmediateBatcher} computes them.
 *
 * <p>The pattern is vertex-format agnostic: it only assumes four consecutive vertices per quad.
 */
public final class SharedQuadIndex {

    /** Largest quad count the shared buffer covers: 65,536 quads, 1.5 MiB of uint32 indices. */
    public static final int MAX_QUADS = 65536;

    public static final int INDICES_PER_QUAD = 6;
    public static final int VERTICES_PER_QUAD = 4;

    private SharedQuadIndex() {
    }

    public static int indexCountForQuads(int quads) {
        checkQuadCount(quads);
        return quads * INDICES_PER_QUAD;
    }

    public static int byteSizeForQuads(int quads) {
        return indexCountForQuads(quads) * Integer.BYTES;
    }

    /** Generates the full pattern for {@code quads} quads as a fresh array (tests, tooling). */
    public static int[] generate(int quads) {
        int[] indices = new int[indexCountForQuads(quads)];
        fill(indices, quads);
        return indices;
    }

    static void fill(int[] indices, int quads) {
        int p = 0;
        for (int quad = 0; quad < quads; quad++) {
            int base = quad * VERTICES_PER_QUAD;
            indices[p++] = base;
            indices[p++] = base + 1;
            indices[p++] = base + 2;
            indices[p++] = base;
            indices[p++] = base + 2;
            indices[p++] = base + 3;
        }
    }

    /**
     * Writes the pattern for {@code quads} quads into {@code destination} at its current position,
     * in little-endian order (the byte order every target GPU platform reads indices in). The
     * buffer's order is set to little-endian as a side effect.
     */
    public static void fill(ByteBuffer destination, int quads) {
        int count = indexCountForQuads(quads);
        if (destination.remaining() < count * Integer.BYTES) {
            throw new IllegalArgumentException("destination holds " + destination.remaining()
                    + " bytes, pattern needs " + count * Integer.BYTES);
        }
        destination.order(ByteOrder.LITTLE_ENDIAN);
        for (int quad = 0; quad < quads; quad++) {
            int base = quad * VERTICES_PER_QUAD;
            destination.putInt(base);
            destination.putInt(base + 1);
            destination.putInt(base + 2);
            destination.putInt(base);
            destination.putInt(base + 2);
            destination.putInt(base + 3);
        }
    }

    private static void checkQuadCount(int quads) {
        if (quads < 0 || quads > MAX_QUADS) {
            throw new IllegalArgumentException("quads " + quads + " outside 0.." + MAX_QUADS);
        }
    }
}
