package org.embeddedt.embeddium.impl.model.quad.properties;

import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import org.embeddedt.embeddium.impl.model.quad.ModelQuadView;
import org.embeddedt.embeddium.api.util.ColorABGR;

public class ModelQuadFlags {
    /**
     * Indicates that the quad does not fully cover the given face for the model.
     */
    public static final int IS_PARTIAL = 0b001;

    /**
     * Indicates that the quad is parallel to its light face.
     */
    public static final int IS_PARALLEL = 0b010;

    /**
     * Indicates that the quad is aligned to the block grid.
     * This flag is only set if {@link #IS_PARALLEL} is set.
     */
    public static final int IS_ALIGNED = 0b100;

    /**
     * Indicates that the quad should be shaded using vanilla's getShade logic and the light face, rather than
     * the normals of each vertex.
     */
    public static final int IS_VANILLA_SHADED = 0b1000;
    /**
     * Indicates that the particle sprite on this quad can be trusted to be the only sprite it shows.
     */
    public static final int IS_TRUSTED_SPRITE = (1 << 4);
    /**
     * Indicates that this quad can use a more optimal terrain render pass based on its sprite.
     */
    public static final int IS_PASS_OPTIMIZABLE = (1 << 5);
    /**
     * Indicates that the flags are populated for the quad.
     */
    public static final int IS_POPULATED = (1 << 31);

    /**
     * @return True if the bit-flag of {@link ModelQuadFlags} contains the given flag
     */
    public static boolean contains(int flags, int mask) {
        return (flags & mask) != 0;
    }

    public static int getQuadFlags(ModelQuadView quad, ModelQuadFacing face) {
        return getQuadFlags(quad, face, 0);
    }

    /**
     * Checks whether a quad's vertex ordering matches Minecraft's canonical baked order
     * for the given face.
     * <p>
     * Minecraft allows four different permutations of a quad's vertices that will
     * render identically. However, the baked model pipeline (e.g. ambient occlusion,
     * lighting interpolation, and back-face culling) assumes a specific ordering
     * that is hardcoded in the model baking logic. This method verifies
     * that a quad's vertices are listed in that exact canonical order.
     * <p>
     * The canonical order is:
     * <ul>
     *   <li>Vertices are arranged counter-clockwise (CCW) as seen from outside the block face.</li>
     *   <li>Each face (±X, ±Y, ±Z) starts at a specific corner and proceeds CCW around the face.</li>
     *   <li>For example, the -Y face (DOWN) starts at {@code (minX, minY, maxZ)} and proceeds
     *       through {@code (minX, minY, minZ)}, {@code (maxX, minY, minZ)}, {@code (maxX, minY, maxZ)}.</li>
     * </ul>
     * <p>
     * This method avoids allocations by comparing directly against the expected coordinates
     * for each vertex index based on the given {@code face} and bounding box.
     *
     * @param face the face direction of the quad; determines which axis is fixed and
     *             which two axes form the quad plane
     * @return {@code true} if the quad's vertices match Minecraft's canonical baked order
     *         for the given face, {@code false} otherwise
     */
    private static boolean canonicalVertexOrder(
            ModelQuadFacing face,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ) {
        return switch (face) {
            case NEG_Y -> MathUtil.roughlyEqual(x0, minX) && MathUtil.roughlyEqual(y0, minY) && MathUtil.roughlyEqual(z0, maxZ)
                    && MathUtil.roughlyEqual(x1, minX) && MathUtil.roughlyEqual(y1, minY) && MathUtil.roughlyEqual(z1, minZ)
                    && MathUtil.roughlyEqual(x2, maxX) && MathUtil.roughlyEqual(y2, minY) && MathUtil.roughlyEqual(z2, minZ)
                    && MathUtil.roughlyEqual(x3, maxX) && MathUtil.roughlyEqual(y3, minY) && MathUtil.roughlyEqual(z3, maxZ);
            case POS_Y -> MathUtil.roughlyEqual(x0, minX) && MathUtil.roughlyEqual(y0, maxY) && MathUtil.roughlyEqual(z0, minZ)
                    && MathUtil.roughlyEqual(x1, minX) && MathUtil.roughlyEqual(y1, maxY) && MathUtil.roughlyEqual(z1, maxZ)
                    && MathUtil.roughlyEqual(x2, maxX) && MathUtil.roughlyEqual(y2, maxY) && MathUtil.roughlyEqual(z2, maxZ)
                    && MathUtil.roughlyEqual(x3, maxX) && MathUtil.roughlyEqual(y3, maxY) && MathUtil.roughlyEqual(z3, minZ);
            case NEG_Z -> MathUtil.roughlyEqual(x0, maxX) && MathUtil.roughlyEqual(y0, maxY) && MathUtil.roughlyEqual(z0, minZ)
                    && MathUtil.roughlyEqual(x1, maxX) && MathUtil.roughlyEqual(y1, minY) && MathUtil.roughlyEqual(z1, minZ)
                    && MathUtil.roughlyEqual(x2, minX) && MathUtil.roughlyEqual(y2, minY) && MathUtil.roughlyEqual(z2, minZ)
                    && MathUtil.roughlyEqual(x3, minX) && MathUtil.roughlyEqual(y3, maxY) && MathUtil.roughlyEqual(z3, minZ);
            case POS_Z -> MathUtil.roughlyEqual(x0, minX) && MathUtil.roughlyEqual(y0, maxY) && MathUtil.roughlyEqual(z0, maxZ)
                    && MathUtil.roughlyEqual(x1, minX) && MathUtil.roughlyEqual(y1, minY) && MathUtil.roughlyEqual(z1, maxZ)
                    && MathUtil.roughlyEqual(x2, maxX) && MathUtil.roughlyEqual(y2, minY) && MathUtil.roughlyEqual(z2, maxZ)
                    && MathUtil.roughlyEqual(x3, maxX) && MathUtil.roughlyEqual(y3, maxY) && MathUtil.roughlyEqual(z3, maxZ);
            case NEG_X -> MathUtil.roughlyEqual(x0, minX) && MathUtil.roughlyEqual(y0, maxY) && MathUtil.roughlyEqual(z0, minZ)
                    && MathUtil.roughlyEqual(x1, minX) && MathUtil.roughlyEqual(y1, minY) && MathUtil.roughlyEqual(z1, minZ)
                    && MathUtil.roughlyEqual(x2, minX) && MathUtil.roughlyEqual(y2, minY) && MathUtil.roughlyEqual(z2, maxZ)
                    && MathUtil.roughlyEqual(x3, minX) && MathUtil.roughlyEqual(y3, maxY) && MathUtil.roughlyEqual(z3, maxZ);
            case POS_X -> MathUtil.roughlyEqual(x0, maxX) && MathUtil.roughlyEqual(y0, maxY) && MathUtil.roughlyEqual(z0, maxZ)
                    && MathUtil.roughlyEqual(x1, maxX) && MathUtil.roughlyEqual(y1, minY) && MathUtil.roughlyEqual(z1, maxZ)
                    && MathUtil.roughlyEqual(x2, maxX) && MathUtil.roughlyEqual(y2, minY) && MathUtil.roughlyEqual(z2, minZ)
                    && MathUtil.roughlyEqual(x3, maxX) && MathUtil.roughlyEqual(y3, maxY) && MathUtil.roughlyEqual(z3, minZ);
            case UNASSIGNED -> false;
        };
    }

    /**
     * Calculates the properties of the given quad. This data is used later by the light pipeline in order to make
     * certain optimizations.
     */
    public static int getQuadFlags(ModelQuadView quad, ModelQuadFacing face, int existingFlags) {
        int numVertices = 4;
        if (quad instanceof BakedQuadView bakedQuad) {
            numVertices = Math.min(numVertices, bakedQuad.getVerticesCount());
        }

        float minX = 32.0F, minY = 32.0F, minZ = 32.0F;
        float maxX = -32.0F, maxY = -32.0F, maxZ = -32.0F;
        float x0 = 0.0F, y0 = 0.0F, z0 = 0.0F;
        float x1 = 0.0F, y1 = 0.0F, z1 = 0.0F;
        float x2 = 0.0F, y2 = 0.0F, z2 = 0.0F;
        float x3 = 0.0F, y3 = 0.0F, z3 = 0.0F;
        boolean degenerate = false, nonOpaqueColor = false;

        if (numVertices == 4) {
            x0 = quad.getX(0); y0 = quad.getY(0); z0 = quad.getZ(0);
            x1 = quad.getX(1); y1 = quad.getY(1); z1 = quad.getZ(1);
            x2 = quad.getX(2); y2 = quad.getY(2); z2 = quad.getZ(2);
            x3 = quad.getX(3); y3 = quad.getY(3); z3 = quad.getZ(3);

            minX = Math.min(Math.min(x0, x1), Math.min(x2, x3));
            minY = Math.min(Math.min(y0, y1), Math.min(y2, y3));
            minZ = Math.min(Math.min(z0, z1), Math.min(z2, z3));
            maxX = Math.max(Math.max(x0, x1), Math.max(x2, x3));
            maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));
            maxZ = Math.max(Math.max(z0, z1), Math.max(z2, z3));

            degenerate = (MathUtil.roughlyEqual(x0, x1) && MathUtil.roughlyEqual(y0, y1) && MathUtil.roughlyEqual(z0, z1))
                    || (MathUtil.roughlyEqual(x0, x2) && MathUtil.roughlyEqual(y0, y2) && MathUtil.roughlyEqual(z0, z2))
                    || (MathUtil.roughlyEqual(x1, x2) && MathUtil.roughlyEqual(y1, y2) && MathUtil.roughlyEqual(z1, z2))
                    || (MathUtil.roughlyEqual(x0, x3) && MathUtil.roughlyEqual(y0, y3) && MathUtil.roughlyEqual(z0, z3))
                    || (MathUtil.roughlyEqual(x1, x3) && MathUtil.roughlyEqual(y1, y3) && MathUtil.roughlyEqual(z1, z3))
                    || (MathUtil.roughlyEqual(x2, x3) && MathUtil.roughlyEqual(y2, y3) && MathUtil.roughlyEqual(z2, z3));

            nonOpaqueColor = ((quad.getColor(0) & quad.getColor(1) & quad.getColor(2) & quad.getColor(3)) >>> 24) != 255;
        } else {
            for (int i = 0; i < numVertices; ++i) {
                float x = quad.getX(i);
                float y = quad.getY(i);
                float z = quad.getZ(i);

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);

                for (int j = 0; j < i; ++j) {
                    if (MathUtil.roughlyEqual(quad.getX(j), x)
                            && MathUtil.roughlyEqual(quad.getY(j), y)
                            && MathUtil.roughlyEqual(quad.getZ(j), z)) {
                        degenerate = true;
                        break;
                    }
                }

                if (ColorABGR.unpackAlpha(quad.getColor(i)) != 255) {
                    nonOpaqueColor = true;
                }
            }
        }

        // Do not set the partial flag if the vertices are not listed in the expected order. The optimization assumes
        // it can map directly onto corners in specific locations.
        boolean partial = degenerate || (switch (face.getAxis()) {
            case X -> minY >= 0.0001f || minZ >= 0.0001f || maxY <= 0.9999F || maxZ <= 0.9999F;
            case Y -> minX >= 0.0001f || minZ >= 0.0001f || maxX <= 0.9999F || maxZ <= 0.9999F;
            case Z -> minX >= 0.0001f || minY >= 0.0001f || maxX <= 0.9999F || maxY <= 0.9999F;
        }) || numVertices != 4 || !canonicalVertexOrder(face, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, minX, minY, minZ, maxX, maxY, maxZ);

        boolean parallel = switch(face.getAxis()) {
            case X -> minX == maxX;
            case Y -> minY == maxY;
            case Z -> minZ == maxZ;
        };

        boolean aligned = parallel && switch (face) {
            case NEG_Y -> minY < 0.0001f;
            case POS_Y -> maxY > 0.9999F;
            case NEG_Z -> minZ < 0.0001f;
            case POS_Z -> maxZ > 0.9999F;
            case NEG_X -> minX < 0.0001f;
            case POS_X -> maxX > 0.9999F;
            case UNASSIGNED -> throw new IllegalArgumentException();
        };

        int flags = existingFlags & ~(IS_PARTIAL | IS_PARALLEL | IS_ALIGNED);

        if (partial) {
            flags |= IS_PARTIAL;
        }

        if (parallel) {
            flags |= IS_PARALLEL;
        }

        if (aligned) {
            flags |= IS_ALIGNED;
        }

        if (!nonOpaqueColor && (flags & IS_TRUSTED_SPRITE) != 0) {
            flags |= IS_PASS_OPTIMIZABLE;
        }

        flags |= IS_POPULATED;

        return flags;
    }
}