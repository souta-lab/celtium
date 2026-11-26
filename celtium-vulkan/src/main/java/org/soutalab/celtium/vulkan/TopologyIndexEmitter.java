package org.soutalab.celtium.vulkan;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.batch.SharedQuadIndex;

/**
 * Expands Minecraft's primitive topologies into triangle/line-list indices, writing them straight
 * into mapped memory instead of materialising them as a Java array.
 * <p>
 * This is the successor of {@code VulkanImmediateDrawer.expandIndices} and its 512-slot
 * {@code int[]} cache, which Stage 5 slice S5-0 removes: the heap arrays and the cache are gone,
 * and the only caller-owned state is the destination stream itself (a {@link VulkanDynamicMesh}'s
 * index slice, which the ring unification later makes a {@code FrameLinearRing} region - the
 * emitter only ever sees the mapped address, so that move does not change this class).
 * <p>
 * {@code GL_QUADS} is the one mode whose expansion normally never reaches this class: a quad-only
 * mesh addresses the shared pattern in {@link SharedQuadIndex} instead and writes no indices at
 * all. The quad case is still implemented here for the meshes that already hold streamed indices
 * when a quad batch joins them, and its output is the shared pattern shifted by the base vertex -
 * the equivalence test pins the two producers to the same sequence.
 * <p>
 * The acceptance contract is the old expansion's, unchanged: {@link #indexCount} answers {@code -1}
 * exactly where {@code expandIndices} returned {@code null}, and the same lengths everywhere else,
 * including {@code 0} for a non-positive vertex count (which the old code answered with an empty
 * array for every mode, known or not).
 */
public final class TopologyIndexEmitter {

    private TopologyIndexEmitter() {
    }

    /**
     * {@return how many indices the expansion of {@code vertexCount} vertices under {@code glMode}
     * produces, or {@code -1} when this renderer has no equivalent for the pair}.
     */
    public static int indexCount(int glMode, int vertexCount) {
        if (vertexCount <= 0) {
            return 0;
        }
        switch (glMode) {
            case GL11.GL_QUADS:
                return vertexCount % 4 != 0 ? -1 : (vertexCount / 4) * SharedQuadIndex.INDICES_PER_QUAD;
            case GL11.GL_TRIANGLES:
            case GL11.GL_LINES:
                return glMode == GL11.GL_LINES && vertexCount % 2 != 0 ? -1 : vertexCount;
            case GL11.GL_TRIANGLE_FAN:
            case GL11.GL_TRIANGLE_STRIP:
            case GL11.GL_POLYGON:
                return vertexCount < 2 || (glMode == GL11.GL_POLYGON && vertexCount < 3)
                        ? -1 : (vertexCount - 2) * 3;
            case GL11.GL_LINE_STRIP:
                return vertexCount < 2 ? -1 : (vertexCount - 1) * 2;
            case GL11.GL_LINE_LOOP:
                return vertexCount < 2 ? -1 : vertexCount * 2;
            case GL11.GL_QUAD_STRIP:
                return vertexCount < 4 ? -1 : ((vertexCount - 2) / 2) * SharedQuadIndex.INDICES_PER_QUAD;
            default:
                return -1;
        }
    }

    /**
     * Writes the expansion of {@code vertexCount} vertices under {@code glMode} as uint32 indices
     * at {@code dstAddress}, each value offset by {@code baseVertex} (the position of this batch's
     * first vertex inside the destination mesh).
     * <p>
     * The caller must have established {@code indexCount(glMode, vertexCount) > 0} and reserved
     * that many uint32 slots at the destination; this method writes exactly that many.
     *
     * @return the number of indices written, equal to {@code indexCount(glMode, vertexCount)}
     */
    public static int writeIndices(int glMode, int vertexCount, int baseVertex, long dstAddress) {
        int out = 0;
        switch (glMode) {
            case GL11.GL_QUADS: {
                int quads = vertexCount / 4;
                for (int quad = 0; quad < quads; quad++) {
                    int base = baseVertex + quad * SharedQuadIndex.VERTICES_PER_QUAD;
                    out = put(dstAddress, out, base);
                    out = put(dstAddress, out, base + 1);
                    out = put(dstAddress, out, base + 2);
                    out = put(dstAddress, out, base);
                    out = put(dstAddress, out, base + 2);
                    out = put(dstAddress, out, base + 3);
                }
                return out;
            }
            case GL11.GL_TRIANGLES:
            case GL11.GL_LINES: {
                // The vertex order is already the index order; the old code generated the identity
                // array for these, and the stream keeps it (the non-indexed draw form belongs to
                // the batcher's per-entry draw model, not to a group's single indexed draw).
                for (int i = 0; i < vertexCount; i++) {
                    out = put(dstAddress, out, baseVertex + i);
                }
                return out;
            }
            case GL11.GL_TRIANGLE_FAN:
            case GL11.GL_POLYGON: {
                // A fan, and a convex polygon is the fan of its vertices - what GL's polygon
                // rasterizer produces there.
                for (int i = 0; i < vertexCount - 2; i++) {
                    out = put(dstAddress, out, baseVertex);
                    out = put(dstAddress, out, baseVertex + i + 1);
                    out = put(dstAddress, out, baseVertex + i + 2);
                }
                return out;
            }
            case GL11.GL_LINE_STRIP:
            case GL11.GL_LINE_LOOP: {
                // A strip of n vertices is n-1 segments; a loop adds the closing one. Both are
                // drawn as a line list.
                int segments = glMode == GL11.GL_LINE_LOOP ? vertexCount : vertexCount - 1;
                for (int segment = 0; segment < segments; segment++) {
                    out = put(dstAddress, out, baseVertex + segment);
                    out = put(dstAddress, out, baseVertex + (segment + 1) % vertexCount);
                }
                return out;
            }
            case GL11.GL_QUAD_STRIP: {
                // Each pair of vertices with the next pair forms a quad: (2i, 2i+1, 2i+3, 2i+2).
                int quads = (vertexCount - 2) / 2;
                for (int i = 0; i < quads; i++) {
                    int a = baseVertex + i * 2;
                    int b = a + 1;
                    int c = a + 3;
                    int d = a + 2;
                    out = put(dstAddress, out, a);
                    out = put(dstAddress, out, b);
                    out = put(dstAddress, out, c);
                    out = put(dstAddress, out, a);
                    out = put(dstAddress, out, c);
                    out = put(dstAddress, out, d);
                }
                return out;
            }
            case GL11.GL_TRIANGLE_STRIP: {
                for (int i = 0; i < vertexCount - 2; i++) {
                    // Odd triangles swap the first two vertices to keep the winding consistent.
                    if ((i & 1) == 0) {
                        out = put(dstAddress, out, baseVertex + i);
                        out = put(dstAddress, out, baseVertex + i + 1);
                        out = put(dstAddress, out, baseVertex + i + 2);
                    } else {
                        out = put(dstAddress, out, baseVertex + i + 1);
                        out = put(dstAddress, out, baseVertex + i);
                        out = put(dstAddress, out, baseVertex + i + 2);
                    }
                }
                return out;
            }
            default:
                throw new IllegalArgumentException("no index expansion for draw mode " + glMode);
        }
    }

    private static int put(long dstAddress, int out, int value) {
        MemoryUtil.memPutInt(dstAddress + (long) out * 4L, value);
        return out + 1;
    }
}
