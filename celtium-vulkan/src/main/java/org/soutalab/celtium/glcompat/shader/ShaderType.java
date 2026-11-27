package org.soutalab.celtium.glcompat.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

/**
 * Celtium's own enumeration of the OpenGL shader stages the direct-GL converter handles.
 * Each constant carries the GL enum value ({@code GL_*_SHADER}) that names the stage on
 * the converter's GL-facing side, so a stage can be carried through the transform pipeline
 * as a type instead of a bare int.
 */
public enum ShaderType {
    VERTEX(GL20.GL_VERTEX_SHADER),
    GEOMETRY(GL32.GL_GEOMETRY_SHADER),
    TESSELATION_CONTROL(GL40.GL_TESS_CONTROL_SHADER),
    TESSELATION_EVAL(GL40.GL_TESS_EVALUATION_SHADER),
    FRAGMENT(GL20.GL_FRAGMENT_SHADER),
    COMPUTE(GL43.GL_COMPUTE_SHADER);

    /** The GL enum value naming this stage. */
    public final int id;

    ShaderType(int id) {
        this.id = id;
    }
}
