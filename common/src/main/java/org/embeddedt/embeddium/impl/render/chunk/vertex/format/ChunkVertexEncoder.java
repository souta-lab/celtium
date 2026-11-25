package org.embeddedt.embeddium.impl.render.chunk.vertex.format;

import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;

public interface ChunkVertexEncoder {
    long write(long ptr, Material material, Vertex vertex, int sectionIndex);

    default boolean supportsBilinearCorrection() {
        return true;
    }

    /**
     * Whether this encoder drops the per-vertex bilinear correction factor from the vertex
     * stream and instead wants it recorded once per quad in a side stream (the correction
     * is constant over a quad). When true, the buffer builder keeps the factors in push
     * order and the built mesh parts carry them next to the vertex data.
     */
    default boolean capturesRdhFactorSideStream() {
        return false;
    }

    class Vertex {
        public float x;
        public float y;
        public float z;
        public int color;
        public int rdhFactor;
        public float u;
        public float v;
        public int light;
        /**
         * The normal that vanilla would output for this quad. Unused by Embeddium's built-in shaders, but might be used
         * by a core shader.
         */
        public int vanillaNormal;
        /**
         * The actual normal vector of this quad computed off the geometry.
         */
        public int trueNormal;

        public static Vertex[] uninitializedQuad() {
            Vertex[] vertices = new Vertex[4];

            for (int i = 0; i < 4; i++) {
                vertices[i] = new Vertex();
            }

            return vertices;
        }

        @Override
        public String toString() {
            return String.format("XYZ: (%.02f, %.02f, %.02f), C: %08x, L: %08x", x, y, z, color, light);
        }
    }
}
