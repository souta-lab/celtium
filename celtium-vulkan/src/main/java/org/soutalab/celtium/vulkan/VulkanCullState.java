package org.soutalab.celtium.vulkan;

import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_BACK_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_FRONT_AND_BACK;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_FRONT_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;

/**
 * GL's face-culling state - the enable, the face and the winding - encoded in the one int the immediate state array
 * carries as its cull slot.
 * <p>
 * The slot used to be a boolean with the answer baked into the pipeline: {@code VK_CULL_MODE_BACK_BIT} with
 * {@code VK_FRONT_FACE_CLOCKWISE}, which is GL's default pair. A mod that chooses {@code GL_FRONT} - the shape a
 * mirrored or inside-out model is drawn with - was therefore culled exactly the wrong way round, and its geometry left
 * no pixels while every state readback agreed with it: the mirror was faithful and nothing read it. The encoding keeps
 * the old answers bit-identical for GL's default state, so a scene that never calls {@code glCullFace} or
 * {@code glFrontFace} selects the same pipeline it always did.
 * <p>
 * The winding mapping is the one the terrain pipeline documents: OpenGL's window coordinates have Y up and Vulkan's
 * framebuffer has Y down, so the sign of a triangle's area flips and GL's counter-clockwise front face is Vulkan's
 * clockwise one. {@code VK_CULL_MODE_FRONT_BIT} then culls the same geometric faces GL's {@code GL_FRONT} does, because
 * both name "the face GL calls front", not "the winding".
 */
public final class VulkanCullState {
    /** Culling off. The old boolean's false, so a disabled state keeps its pipeline. */
    public static final int NONE = 0;
    /** Cull GL's back faces: the default, and the old boolean's true. */
    public static final int BACK = 1;
    /** Cull GL's front faces. */
    public static final int FRONT = 2;
    /** Cull both: the state a renderer uses to draw nothing. */
    public static final int FRONT_AND_BACK = 3;
    /** The low two bits are the cull mode; this bit says GL's front face is {@code GL_CW} instead of the default. */
    public static final int FRONT_FACE_CW = 4;
    private static final int MODE_MASK = 3;

    /** GL enums, spelled out because this class must encode them without a GL context. */
    public static final int GL_FRONT = 0x0404;
    public static final int GL_BACK = 0x0405;
    public static final int GL_FRONT_AND_BACK = 0x0408;
    public static final int GL_CCW = 0x0901;
    public static final int GL_CW = 0x0903;

    private VulkanCullState() {
    }

    /**
     * {@return the encoded state for one captured GL state}.
     * <p>
     * An unknown face is treated as {@code GL_BACK}: GL would have raised {@code GL_INVALID_ENUM} and kept the previous
     * mode, and back culling is the mode every caller that never asks starts with. A disabled cull discards both the
     * face and the winding, so a state that draws with culling off keys itself by nothing it does not use.
     */
    public static int encode(boolean enabled, int glCullFace, int glFrontFace) {
        if (!enabled) {
            return NONE;
        }
        int face;
        switch (glCullFace) {
            case GL_FRONT:
                face = FRONT;
                break;
            case GL_FRONT_AND_BACK:
                face = FRONT_AND_BACK;
                break;
            case GL_BACK:
            default:
                face = BACK;
                break;
        }
        return glFrontFace == GL_CW ? face | FRONT_FACE_CW : face;
    }

    /** {@return the {@code VkCullModeFlagBits} for one encoded state}. */
    public static int vkCullMode(int state) {
        switch (state & MODE_MASK) {
            case BACK:
                return VK_CULL_MODE_BACK_BIT;
            case FRONT:
                return VK_CULL_MODE_FRONT_BIT;
            case FRONT_AND_BACK:
                return VK_CULL_MODE_FRONT_AND_BACK;
            default:
                return VK_CULL_MODE_NONE;
        }
    }

    /** {@return the {@code VkFrontFace} for one encoded state}. */
    public static int vkFrontFace(int state) {
        return (state & FRONT_FACE_CW) != 0 ? VK_FRONT_FACE_COUNTER_CLOCKWISE : VK_FRONT_FACE_CLOCKWISE;
    }

    /** {@return whether culling is on}. The old boolean's question, answered from the encoded slot. */
    public static boolean culls(int state) {
        return state != NONE;
    }

    /** {@return a short name for one encoded state}, for the per-group diagnostics. */
    public static String describe(int state) {
        String mode;
        switch (state & MODE_MASK) {
            case BACK:
                mode = "back";
                break;
            case FRONT:
                mode = "front";
                break;
            case FRONT_AND_BACK:
                mode = "front-and-back";
                break;
            default:
                return "off";
        }
        return mode + ((state & FRONT_FACE_CW) != 0 ? "/cw" : "/ccw");
    }
}
