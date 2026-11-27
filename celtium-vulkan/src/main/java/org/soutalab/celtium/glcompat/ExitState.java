package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.states.AlphaState;
import org.soutalab.celtium.glcompat.states.BlendState;
import org.soutalab.celtium.glcompat.states.Color4;
import org.soutalab.celtium.glcompat.states.ColorMask;
import org.soutalab.celtium.glcompat.states.MaterialState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public final class ExitState {
    public static final int POLYGON_OFFSET_ENABLE_WRITTEN = 1;
    public static final int POLYGON_OFFSET_VALUES_WRITTEN = 2;
    final Values entry = new Values();
    final Values value = new Values();
    final MaterialState frontMaterial = new MaterialState(GL11.GL_FRONT);
    final MaterialState backMaterial = new MaterialState(GL11.GL_BACK);
    long writes, dependencies;
    boolean valid;
    int textureDeleteCount;

    public void clear() {
        valid = false;
        writes = dependencies = 0;
    }

    static final class Values {
        final Color4 color = new Color4();
        final Vector3f normal = new Vector3f();
        final Vector4f texCoord = new Vector4f();
        final Vector4f shaderColor = new Vector4f();
        final Vector4f overlayColor = new Vector4f();
        final BlendState blend = new BlendState();
        final AlphaState alpha = new AlphaState();
        final ColorMask colorMask = new ColorMask();
        final Matrix4f textureMatrix = new Matrix4f();
        long enabled;
        int activeUnit, clientActiveUnit, matrixMode, texture, depthFunction, cullFace, frontFace, shadeModel;
        int materialFace, materialParameter;
        boolean depthMask;
        float lightmapX, lightmapY, offsetFactor, offsetUnits, offsetClamp;
    }
}
