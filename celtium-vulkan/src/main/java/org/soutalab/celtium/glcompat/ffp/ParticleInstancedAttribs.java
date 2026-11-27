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

import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.memPutFloat;
import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.memPutInt;

public final class ParticleInstancedAttribs {

    public static final int LOC_CENTER_HALF = 5;
    public static final int LOC_UV = 6;
    public static final int LOC_COLOR = InstancedAttribs.LOC_COLOR;
    public static final int LOC_LIGHTMAP = InstancedAttribs.LOC_LIGHTMAP;

    public static final int OFFSET_CENTER_HALF = 0;
    public static final int OFFSET_UV = 16;
    public static final int OFFSET_COLOR = 32;
    public static final int OFFSET_LIGHTMAP = 36;
    public static final int STRIDE = 48;

    public static void pointInstanceAttribs(long base) {
        GLSMVertexInput.glVertexAttribPointer(LOC_CENTER_HALF, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_CENTER_HALF);
        GLSMVertexInput.glVertexAttribPointer(LOC_UV, 4, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_UV);
        GLSMVertexInput.glVertexAttribPointer(LOC_COLOR, 4, GL11.GL_UNSIGNED_BYTE, true, STRIDE, base + OFFSET_COLOR);
        GLSMVertexInput.glVertexAttribPointer(LOC_LIGHTMAP, 2, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_LIGHTMAP);
    }

    static void enableInstanceArrays() {
        InstancedAttribs.enableInstanced(LOC_CENTER_HALF);
        InstancedAttribs.enableInstanced(LOC_UV);
        InstancedAttribs.enableInstanced(LOC_COLOR);
        InstancedAttribs.enableInstanced(LOC_LIGHTMAP);
    }

    public static void writeInstance(long ptr, float cx, float cy, float cz, float half, float u0, float v0, float u1, float v1, int colorABGR, int packedLight) {
        memPutFloat(ptr + OFFSET_CENTER_HALF, cx);
        memPutFloat(ptr + OFFSET_CENTER_HALF + 4, cy);
        memPutFloat(ptr + OFFSET_CENTER_HALF + 8, cz);
        memPutFloat(ptr + OFFSET_CENTER_HALF + 12, half);
        memPutFloat(ptr + OFFSET_UV, u0);
        memPutFloat(ptr + OFFSET_UV + 4, v0);
        memPutFloat(ptr + OFFSET_UV + 8, u1);
        memPutFloat(ptr + OFFSET_UV + 12, v1);
        memPutInt(ptr + OFFSET_COLOR, colorABGR);
        InstancedAttribs.writeLightmap(ptr + OFFSET_LIGHTMAP, packedLight);
    }

    private ParticleInstancedAttribs() {}
}
