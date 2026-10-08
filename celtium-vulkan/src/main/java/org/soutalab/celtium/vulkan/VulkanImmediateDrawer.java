package org.soutalab.celtium.vulkan;

import org.lwjgl.opengl.GL11;

/**
 * Translates Minecraft's immediate-mode geometry into a Vulkan submission.
 * <p>
 * This is the draw half of the Forge API swap described in {@code docs/forge-api-vulkan-inventory.md}: the mod-side
 * Mixin on {@code WorldVertexBufferUploader.func_181679_a(BufferBuilder)} hands over the finished buffer, and this
 * class turns it into one batch of {@link VulkanDynamicMesh} plus the pipeline selected from {@link VulkanGlState}.
 * <p>
 * The first thing that has to happen is topology conversion. Minecraft draws with {@code GL_QUADS} and the triangle
 * modes; Vulkan has no quads, so a quad becomes two triangles and the index list is generated here. Modes that have
 * no Vulkan equivalent in this renderer (points, lines) return {@code null} with a reason rather than drawing
 * something wrong.
 */
public final class VulkanImmediateDrawer {

    private VulkanImmediateDrawer() {
    }

    /**
     * Expands Minecraft's primitive topology into triangle-list indices.
     *
     * @param glMode      the Minecraft/OpenGL draw mode
     * @param vertexCount number of vertices in the batch
     * @return triangle-list indices, or {@code null} when this renderer has no equivalent for the mode
     */
    public static int[] expandIndices(int glMode, int vertexCount) {
        if (vertexCount <= 0) {
            return new int[0];
        }
        switch (glMode) {
            case GL11.GL_QUADS: {
                if (vertexCount % 4 != 0) {
                    return null;
                }
                int quads = vertexCount / 4;
                int[] indices = new int[quads * 6];
                for (int quad = 0; quad < quads; quad++) {
                    int base = quad * 4;
                    int out = quad * 6;
                    // Minecraft's quad winding is 0,1,2,3; both triangles keep it.
                    indices[out] = base;
                    indices[out + 1] = base + 1;
                    indices[out + 2] = base + 2;
                    indices[out + 3] = base;
                    indices[out + 4] = base + 2;
                    indices[out + 5] = base + 3;
                }
                return indices;
            }
            case GL11.GL_TRIANGLES: {
                int[] indices = new int[vertexCount];
                for (int i = 0; i < vertexCount; i++) {
                    indices[i] = i;
                }
                return indices;
            }
            case GL11.GL_TRIANGLE_FAN: {
                int[] indices = new int[(vertexCount - 2) * 3];
                for (int i = 0; i < vertexCount - 2; i++) {
                    indices[i * 3] = 0;
                    indices[i * 3 + 1] = i + 1;
                    indices[i * 3 + 2] = i + 2;
                }
                return indices;
            }
            case GL11.GL_TRIANGLE_STRIP: {
                int[] indices = new int[(vertexCount - 2) * 3];
                for (int i = 0; i < vertexCount - 2; i++) {
                    // Odd triangles swap the first two vertices to keep the winding consistent.
                    if ((i & 1) == 0) {
                        indices[i * 3] = i;
                        indices[i * 3 + 1] = i + 1;
                        indices[i * 3 + 2] = i + 2;
                    } else {
                        indices[i * 3] = i + 1;
                        indices[i * 3 + 1] = i;
                        indices[i * 3 + 2] = i + 2;
                    }
                }
                return indices;
            }
            default:
                return null;
        }
    }

    /** {@return a human-readable reason why a mode is not drawable here, or null when it is} */
    public static String unsupportedReason(int glMode) {
        switch (glMode) {
            case GL11.GL_QUADS:
            case GL11.GL_TRIANGLES:
            case GL11.GL_TRIANGLE_FAN:
            case GL11.GL_TRIANGLE_STRIP:
                return null;
            case GL11.GL_LINES:
            case GL11.GL_LINE_STRIP:
            case GL11.GL_LINE_LOOP:
                return "line primitives are not implemented in the Vulkan terrain renderer yet";
            case GL11.GL_POINTS:
                return "point primitives are not implemented in the Vulkan terrain renderer yet";
            default:
                return "unknown draw mode " + glMode;
        }
    }
}
