package org.soutalab.celtium.glcompat.gtnh.client.renderer.vao;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import static org.soutalab.celtium.glcompat.gtnh.client.renderer.vao.IndexBuffer.*;

import org.lwjgl.opengl.GL11;

import org.soutalab.celtium.glcompat.gtnh.client.renderer.vbo.IVertexBuffer;

public class IndexedVAO extends BaseVAO {

    private final IndexBuffer ebo;

    public IndexedVAO(IVertexBuffer vbo, IndexBuffer ebo) {
        super(vbo);
        this.ebo = ebo;
    }

    @Override
    protected void setupVAOStates() {
        super.setupVAOStates();
        ebo.bind();
    }

    @Override
    public void delete() {
        super.delete();
        ebo.delete();
    }

    @Override
    public void draw() {
        GL11.glDrawElements(vbo.getDrawMode(), vbo.getVertexCount(), EBO_DATA_TYPE, 0);
    }

    @Override
    public void draw(int first, int count) {
        GL11.glDrawElements(vbo.getDrawMode(), count, EBO_DATA_TYPE, first * EBO_DATA_SIZE);
    }

    @Override
    public void draw(int drawMode, int first, int count) {
        GL11.glDrawElements(drawMode, count, EBO_DATA_TYPE, first * EBO_DATA_SIZE);
    }

    public final IndexBuffer getEBO() {
        return this.ebo;
    }
}
