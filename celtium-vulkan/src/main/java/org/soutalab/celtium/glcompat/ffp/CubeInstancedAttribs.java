package org.soutalab.celtium.glcompat.ffp;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLSMVertexInput;
import org.lwjgl.opengl.GL11;

public final class CubeInstancedAttribs {

    public static final int LOC_LIGHTMAP_SCALE = InstancedAttribs.LOC_LIGHTMAP;
    public static final int LOC_CUBE_TEX = 15;

    public static final int OFFSET_LIGHTMAP_SCALE = InstancedAttribs.HEAD_SIZE;
    public static final int OFFSET_CUBE_TEX = OFFSET_LIGHTMAP_SCALE + 16;
    public static final int STRIDE = OFFSET_CUBE_TEX + 16;

    public static void pointInstanceAttribs(long base) {
        InstancedAttribs.pointHead(base, STRIDE);
        GLSMVertexInput.glVertexAttribPointer(LOC_LIGHTMAP_SCALE, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_LIGHTMAP_SCALE);
        GLSMVertexInput.glVertexAttribPointer(LOC_CUBE_TEX, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_CUBE_TEX);
    }

    static void enableInstanceArrays() {
        InstancedAttribs.enableHeadArrays();
        InstancedAttribs.enableInstanced(LOC_CUBE_TEX);
    }

    private CubeInstancedAttribs() {}
}
