package org.soutalab.celtium.glcompat.ffp;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLSMBlend;
import org.soutalab.celtium.glcompat.GLSMCurrentAttribs;
import org.soutalab.celtium.glcompat.GLSMEnable;
import org.soutalab.celtium.glcompat.GLSMLighting;
import org.soutalab.celtium.glcompat.GLSMMatrices;
import org.soutalab.celtium.glcompat.GLSMPrograms;
import org.soutalab.celtium.glcompat.GLSMTextures;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class CombinedGlint {
    private static final Matrix4f savedMatrix = new Matrix4f();
    private static final Matrix4f inverse = new Matrix4f();
    private static final Matrix4f relative = new Matrix4f();
    private static boolean active;
    private static boolean replaceAlpha;

    private CombinedGlint() {}

    public static boolean isActive() {
        return active;
    }

    public static boolean replacesAlpha() {
        return active && replaceAlpha;
    }

    public static boolean begin(Matrix4fc secondTextureMatrix) {
        if (active || GLSMPrograms.getActiveProgram() != 0 || GLSMLighting.getLightingState().isEnabled() || GLSMCurrentAttribs.getColorSumState().isEnabled() || !GLSMEnable.glIsEnabled(GL11.GL_BLEND)) return false;
        final var blend = GLSMBlend.getBlendState();
        final boolean lastAlpha = blend.getSrcAlpha() == GL11.GL_ONE && blend.getDstAlpha() == GL11.GL_ZERO && blend.getEquationAlpha() == GL14.GL_FUNC_ADD;
        final boolean preserveAlpha = blend.getSrcAlpha() == GL11.GL_ZERO && blend.getDstAlpha() == GL11.GL_ONE && (blend.getEquationAlpha() == GL14.GL_FUNC_ADD || blend.getEquationAlpha() == GL14.GL_FUNC_REVERSE_SUBTRACT);
        if (blend.getSrcRgb() != GL11.GL_SRC_COLOR || blend.getDstRgb() != GL11.GL_ONE
            || (!lastAlpha && !preserveAlpha && (blend.getSrcAlpha() != GL11.GL_SRC_COLOR || blend.getDstAlpha() != GL11.GL_ONE || blend.getEquationAlpha() != blend.getEquationRgb()))
            || (blend.getEquationRgb() != GL14.GL_FUNC_ADD && blend.getEquationRgb() != GL14.GL_FUNC_REVERSE_SUBTRACT)) return false;
        final var textures = GLSMTextures.getTextures();
        if (!textures.getTextureUnitStates(0).isEnabled() || textures.getTextureUnitBindings(0).getBinding() == 0 || textures.getTexEnvState(0).mode != GL11.GL_MODULATE) return false;
        if (textures.getTextureUnitStates(1).isEnabled() && textures.getTextureUnitBindings(1).getBinding() != 0 && textures.getTexEnvState(1).mode != GL11.GL_MODULATE) return false;
        for (int unit = 2; unit < 4; unit++) {
            if (textures.getTextureUnitStates(unit).isEnabled() && textures.getTextureUnitBindings(unit).getBinding() != 0) return false;
        }
        inverse.set(textures.getTextureUnitMatrix(0)).invert();
        relative.set(secondTextureMatrix).mul(inverse);
        if (!relative.isFinite()) return false;
        savedMatrix.set(textures.getTextureUnitMatrix(3));
        GLSMMatrices.setTextureMatrix(3, relative);
        replaceAlpha = lastAlpha;
        active = true;
        return true;
    }

    public static void end() {
        if (!active) return;
        active = false;
        GLSMMatrices.setTextureMatrix(3, savedMatrix);
    }
}
