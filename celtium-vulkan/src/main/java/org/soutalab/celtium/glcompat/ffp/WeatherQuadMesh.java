package org.soutalab.celtium.glcompat.ffp;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.VertexFlags;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.VertexFormatElement;
import org.soutalab.celtium.glcompat.GLSMBuffers;
import org.soutalab.celtium.glcompat.GLSMVertexInput;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The four corners every rain and snow column is expanded from. x picks the end of the billboard, y picks
 * the bottom or top edge.
 */
public final class WeatherQuadMesh {

    public static final int VERTEX_FLAGS = VertexFlags.TEXTURE_BIT;
    public static final int VERTEX_COUNT = 4;

    private static final float[] CORNERS = { 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f };

    private static int vao;
    private static int vbo;

    private WeatherQuadMesh() {}

    public static int vao() {
        if (vao != 0) return vao;

        final ByteBuffer data = ByteBuffer.allocateDirect(CORNERS.length * Float.BYTES).order(ByteOrder.nativeOrder());
        for (float corner : CORNERS) data.putFloat(corner);
        data.flip();

        vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);
        vbo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);

        final int corner = VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation();
        GLSMVertexInput.glEnableVertexAttribArray(corner);
        GLSMVertexInput.glVertexAttribPointer(corner, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0L);
        WeatherInstancedAttribs.enableInstanceArrays();

        GLSMVertexInput.glBindVertexArray(0);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return vao;
    }

    public static void bindInstanceBuffer(int bufferId) {
        GLSMVertexInput.glBindVertexArray(vao());
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        WeatherInstancedAttribs.pointInstanceAttribs(0L);
        GLSMVertexInput.glBindVertexArray(0);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public static void delete() {
        if (vbo != 0) {
            GLSMBuffers.glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            GLSMVertexInput.glDeleteVertexArrays(vao);
            vao = 0;
        }
    }
}
