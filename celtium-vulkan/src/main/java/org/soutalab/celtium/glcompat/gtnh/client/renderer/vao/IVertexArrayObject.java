package org.soutalab.celtium.glcompat.gtnh.client.renderer.vao;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import org.soutalab.celtium.glcompat.gtnh.client.renderer.vbo.IVertexBuffer;

public interface IVertexArrayObject {

    void bind();

    void unbind();

    void draw();

    void draw(int first, int count);

    void draw(int drawMode, int first, int count);

    void delete();

    IVertexBuffer getVBO();

    default void render() {
        bind();
        draw();
        unbind();
    }

    default void render(int first, int count) {
        bind();
        draw(first, count);
        unbind();
    }
}
