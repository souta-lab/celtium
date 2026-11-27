package org.soutalab.celtium.glcompat.gtnh.client.renderer.vao;

/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import java.nio.IntBuffer;

public interface VaoFunctions {

    int getCurrentBinding();

    int glGenVertexArrays();

    void glGenVertexArrays(IntBuffer output);

    void glDeleteVertexArrays(int id);

    void glDeleteVertexArrays(IntBuffer ids);

    boolean glIsVertexArray(int id);

    void glBindVertexArray(int id);
}
