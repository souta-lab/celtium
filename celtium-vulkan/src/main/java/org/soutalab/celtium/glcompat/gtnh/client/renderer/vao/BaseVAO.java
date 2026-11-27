package org.soutalab.celtium.glcompat.gtnh.client.renderer.vao;

/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import static org.soutalab.celtium.glcompat.gtnh.client.renderer.vao.VAOManager.VAO;

import org.soutalab.celtium.glcompat.gtnh.client.renderer.vbo.IVertexBuffer;

public class BaseVAO implements IVertexArrayObject {

    protected int vaoID = -1;

    protected final IVertexBuffer vbo;

    public BaseVAO(IVertexBuffer vbo) {
        this.vbo = vbo;
    }

    @Override
    public final void bind() {
        if (isInitialized()) {
            VAO.glBindVertexArray(vaoID);
            return;
        }
        this.vaoID = VAO.glGenVertexArrays();
        VAO.glBindVertexArray(vaoID);
        setupVAOStates();
    }

    @Override
    public final void unbind() {
        VAO.glBindVertexArray(0);
    }

    @Override
    public void draw() {
        vbo.draw();
    }

    @Override
    public void draw(int first, int count) {
        vbo.draw(first, count);
    }

    @Override
    public void draw(int drawMode, int first, int count) {
        vbo.draw(drawMode, first, count);
    }

    @Override
    public void delete() {
        vbo.delete();
        if (vaoID >= 0) {
            VAO.glDeleteVertexArrays(vaoID);
            vaoID = -1;
        }
    }

    @Override
    public final IVertexBuffer getVBO() {
        return this.vbo;
    }

    protected void setupVAOStates() {
        vbo.setupState();
        vbo.unbind();
    }

    protected final boolean isInitialized() {
        return vaoID != -1;
    }
}
