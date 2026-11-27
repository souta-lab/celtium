// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package org.soutalab.celtium.glcompat.shader;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(GL20.GL_VERTEX_SHADER),
	GEOMETRY(GL32.GL_GEOMETRY_SHADER),
	TESSELATION_CONTROL(GL40.GL_TESS_CONTROL_SHADER),
	TESSELATION_EVAL(GL40.GL_TESS_EVALUATION_SHADER),
    FRAGMENT(GL20.GL_FRAGMENT_SHADER),
    COMPUTE(GL43.GL_COMPUTE_SHADER);

    public final int id;

    ShaderType(int id) {
        this.id = id;
    }
}
