package org.soutalab.celtium.glcompat.gtnh.client.renderer.vao;

/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * See THIRD_PARTY_NOTICES.md.
 */


import java.nio.ByteBuffer;

import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.VertexFormat;

public interface VertexBufferFactory {

    IVertexArrayObject allocate(VertexFormat format, int drawMode, ByteBuffer data, int vertexCount);

    IVertexArrayObject allocate(VertexFormat format, int drawMode, ByteBuffer data, int vertexCount, IndexBuffer ebo);
}
