/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.model;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.soutalab.celtium.core.shader.ShaderStage;

public enum ShaderType {
    VERTEX(GL20.GL_VERTEX_SHADER, ShaderStage.VERTEX),
    GEOMETRY(GL32.GL_GEOMETRY_SHADER, ShaderStage.GEOMETRY),
    TESSELATION_CONTROL(GL40.GL_TESS_CONTROL_SHADER, ShaderStage.VERTEX),
    TESSELATION_EVAL(GL40.GL_TESS_EVALUATION_SHADER, ShaderStage.VERTEX),
    FRAGMENT(GL20.GL_FRAGMENT_SHADER, ShaderStage.FRAGMENT),
    COMPUTE(GL43.GL_COMPUTE_SHADER, ShaderStage.COMPUTE);

    public final int id;
    public final ShaderStage stage;

    ShaderType(int id, ShaderStage stage) {
        this.id = id;
        this.stage = stage;
    }
}
