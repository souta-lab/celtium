/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.model;

import org.lwjgl.opengl.GL11;
import org.soutalab.celtium.core.shader.BlendFactor;
import org.soutalab.celtium.shader.ShaderLogger;

import java.util.Optional;

public enum BlendModeFunction {
    ZERO(GL11.GL_ZERO, BlendFactor.ZERO),
    ONE(GL11.GL_ONE, BlendFactor.ONE),
    SRC_COLOR(GL11.GL_SRC_COLOR, BlendFactor.SRC_COLOR),
    ONE_MINUS_SRC_COLOR(GL11.GL_ONE_MINUS_SRC_COLOR, BlendFactor.ONE_MINUS_SRC_COLOR),
    DST_COLOR(GL11.GL_DST_COLOR, BlendFactor.DST_COLOR),
    ONE_MINUS_DST_COLOR(GL11.GL_ONE_MINUS_DST_COLOR, BlendFactor.ONE_MINUS_DST_COLOR),
    SRC_ALPHA(GL11.GL_SRC_ALPHA, BlendFactor.SRC_ALPHA),
    ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA),
    DST_ALPHA(GL11.GL_DST_ALPHA, BlendFactor.DST_ALPHA),
    ONE_MINUS_DST_ALPHA(GL11.GL_ONE_MINUS_DST_ALPHA, BlendFactor.ONE_MINUS_DST_ALPHA),
    SRC_ALPHA_SATURATE(GL11.GL_SRC_ALPHA_SATURATE, BlendFactor.SRC_ALPHA_SATURATE);

    private final int glId;
    private final BlendFactor blendFactor;

    BlendModeFunction(int glId, BlendFactor blendFactor) {
        this.glId = glId;
        this.blendFactor = blendFactor;
    }

    public static Optional<BlendModeFunction> fromString(String name) {
        try {
            return Optional.of(BlendModeFunction.valueOf(name));
        } catch (IllegalArgumentException e) {
            ShaderLogger.LOGGER.warn("Invalid blend mode! " + name);
            return Optional.empty();
        }
    }

    public int getGlId() {
        return glId;
    }

    public BlendFactor toBlendFactor() {
        return blendFactor;
    }

    public static BlendFactor toBlendFactor(int glId) {
        for (BlendModeFunction fn : values()) {
            if (fn.glId == glId) {
                return fn.blendFactor;
            }
        }
        return BlendFactor.ONE;
    }
}
