package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;

/**
 * Gates GL capability bits that do not exist (or raise GL_INVALID_ENUM) on an OpenGL ES context.
 * Desktop contexts are never filtered; clip planes on GLES require GL_EXT_clip_cull_distance.
 */
public final class GLESCaps {

    private GLESCaps() {}

    public static boolean isCapAllowed(int cap, boolean isGLES, boolean hasClipCullDistance) {
        if (!isGLES) return true;
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + GLStateManager.MAX_CLIP_PLANES) {
            return hasClipCullDistance;
        }
        return switch (cap) {
            case GL11.GL_LINE_SMOOTH,
                 GL11.GL_POLYGON_SMOOTH,
                 GL11.GL_COLOR_LOGIC_OP,
                 GL11.GL_POLYGON_OFFSET_POINT,
                 GL11.GL_POLYGON_OFFSET_LINE,
                 GL13.GL_MULTISAMPLE,
                 GL13.GL_SAMPLE_ALPHA_TO_ONE,
                 GL20.GL_VERTEX_PROGRAM_POINT_SIZE,
                 GL30.GL_FRAMEBUFFER_SRGB,
                 GL31.GL_PRIMITIVE_RESTART,
                 GL32.GL_DEPTH_CLAMP -> false;
            default -> true;
        };
    }
}
