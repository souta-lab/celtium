package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.ffp.VertexKey;
import org.soutalab.celtium.glcompat.states.LightModelState;
import org.soutalab.celtium.glcompat.states.LightState;
import org.soutalab.celtium.glcompat.states.MaterialState;
import org.lwjgl.opengl.GL11;

/** Holds the fixed-function lighting state so a capture can be saved and re-applied without touching the live context. */
public final class LightingSnapshot {
    final boolean[] lightEnabled = new boolean[VertexKey.FFP_LIGHT_COUNT];
    final LightState[] lights = new LightState[VertexKey.FFP_LIGHT_COUNT];
    final LightModelState lightModel = new LightModelState();
    final MaterialState frontMaterial = new MaterialState(GL11.GL_FRONT);
    final MaterialState backMaterial = new MaterialState(GL11.GL_BACK);
    boolean colorMaterial;
    int colorMaterialFace;
    int colorMaterialParameter;
    boolean normalize;
    boolean rescaleNormal;

    public LightingSnapshot() {
        for (int i = 0; i < lights.length; i++) {
            lights[i] = new LightState(GL11.GL_LIGHT0 + i);
        }
    }
}
