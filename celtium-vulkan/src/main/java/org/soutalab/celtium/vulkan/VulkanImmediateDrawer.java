package org.soutalab.celtium.vulkan;

import org.lwjgl.opengl.GL11;

/**
 * Translates Minecraft's immediate-mode geometry into a Vulkan submission.
 * <p>
 * This is the draw half of the Forge API swap described in {@code docs/history/forge-api-vulkan-inventory.md}: the mod-side
 * Mixin on {@code WorldVertexBufferUploader.func_181679_a(BufferBuilder)} hands over the finished buffer, and this
 * class turns it into one batch of {@link VulkanDynamicMesh} plus the pipeline selected from {@link VulkanGlState}.
 * <p>
 * The first thing that has to happen is topology conversion. Minecraft draws with {@code GL_QUADS} and the triangle
 * modes; Vulkan has no quads, so a quad becomes two triangles and the index list is generated while the batch is
 * appended - by {@link TopologyIndexEmitter}, which writes it straight into the mesh's index stream, or, for
 * quad-only meshes, by addressing the one shared pattern in
 * {@link org.soutalab.celtium.vulkan.batch.SharedQuadIndex} instead. Until Stage 5 slice S5-0 this class generated
 * the lists itself ({@code expandIndices}) behind a 512-slot cache of {@code int[]} arrays; both are gone, and
 * what remains here is the mode-acceptance authority the offer paths consult.
 * <p>
 * Modes that have no Vulkan equivalent in this renderer (points, lines) are refused with a reason rather than
 * drawing something wrong.
 */
public final class VulkanImmediateDrawer {

    private VulkanImmediateDrawer() {
    }

    /** {@return whether this is a line primitive mode, which the line pipeline can draw}. */
    public static boolean isLineMode(int glMode) {
        return glMode == GL11.GL_LINES || glMode == GL11.GL_LINE_STRIP || glMode == GL11.GL_LINE_LOOP;
    }

    /** {@return a human-readable reason why a mode is not drawable here, or null when it is} */
    public static String unsupportedReason(int glMode) {
        switch (glMode) {
            case GL11.GL_QUADS:
            case GL11.GL_TRIANGLES:
            case GL11.GL_TRIANGLE_FAN:
            case GL11.GL_TRIANGLE_STRIP:
            case GL11.GL_QUAD_STRIP:
            case GL11.GL_POLYGON:
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

    /**
     * {@return the reason the renderer will not draw a mode, or null when it will}.
     * <p>
     * The acceptance rule of the offer path, for callers that only need accept/refuse: line modes are drawn (at the one
     * pixel width the pipelines implement, which the offer path counts as a narrowing rather than a refusal), so the
     * reason {@link #unsupportedReason} gives for them is not an answer to "will this be drawn". The route that offers a
     * run before it is packed - the legacy {@code glBegin} capture - asks this rather than keeping its own list, which
     * is how it had come to refuse the strips and lines the renderer draws.
     */
    public static String refusalReason(int glMode) {
        String reason = unsupportedReason(glMode);
        return reason != null && isLineMode(glMode) ? null : reason;
    }
}
