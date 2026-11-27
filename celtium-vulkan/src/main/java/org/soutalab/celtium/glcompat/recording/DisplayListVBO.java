package org.soutalab.celtium.glcompat.recording;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.gtnh.client.renderer.vao.IVertexArrayObject;
import org.soutalab.celtium.glcompat.GLSMDraws;
import org.soutalab.celtium.glcompat.GLSMVertexInput;
import org.soutalab.celtium.glcompat.streaming.LineQuads;
import org.lwjgl.opengl.GL11;

/**
 * A class that stores multiple vertex formats & their corresponding buffers.
 */
public final class DisplayListVBO {

    private final SubVBO[] vbos;

    DisplayListVBO(SubVBO[] vbos) {
        this.vbos = vbos;
    }

    public void delete() {
        for (SubVBO vbo : vbos) {
            vbo.delete();
        }
    }

    public SubVBO[] getVBOs() {
        return vbos;
    }

    public void render(int index) {
        vbos[index].render();
    }

    public static final class SubVBO {

        private final IVertexArrayObject vao;
        private final int drawMode;
        private final int start;
        private final int count;
        private IVertexArrayObject lineQuads;
        private int lineQuadStart;
        private int lineQuadCount;
        private int lineQuadFlags;

        void setLineQuads(IVertexArrayObject quads, int start, int count, int flags) {
            this.lineQuads = quads;
            this.lineQuadStart = start;
            this.lineQuadCount = count;
            this.lineQuadFlags = flags;
        }

        public SubVBO(IVertexArrayObject vao, int drawMode, int start, int count) {
            this.vao = vao;
            this.drawMode = drawMode;
            this.start = start;
            this.count = count;
        }

        public void delete() {
            vao.delete();
            if (lineQuads != null) lineQuads.delete();
        }

        public void render() {
            if (vao == null) return;
            final int callerVao = GLSMVertexInput.getBoundVAO();
            try {
                if (lineQuads != null && GLSMDraws.ffpWidensLineQuads()) {
                    renderLineQuads();
                } else {
                    vao.bind();
                    try {
                        vao.draw(drawMode, start, count);
                    } finally {
                        vao.unbind();
                    }
                }
            } finally {
                GLSMVertexInput.glBindVertexArray(callerVao);
            }
        }

        private void renderLineQuads() {
            lineQuads.bind();
            try {
                final boolean culled = LineQuads.beginDraw(lineQuadFlags);
                try {
                    lineQuads.draw(GL11.GL_TRIANGLES, lineQuadStart, lineQuadCount);
                } finally {
                    LineQuads.endDraw(culled);
                }
            } finally {
                lineQuads.unbind();
            }
        }

        public IVertexArrayObject getVAO() {
            return vao;
        }
    }
}
