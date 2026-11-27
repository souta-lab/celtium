package org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.*;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;

import org.soutalab.celtium.glcompat.gtnh.client.renderer.vao.IVertexArrayObject;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vao.IndexBuffer;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vao.VertexBufferType;

public final class VertexOptimizer {

    public static IVertexArrayObject optimizeQuads(VertexBufferType type, VertexFormat format, int vertexCount,
            ByteBuffer data) {

        return type.allocate(
                format,
                GL11.GL_TRIANGLES,
                data,
                vertexCount / 4 * 6,
                IndexBuffer.convertQuadsToTrigs(vertexCount));
    }
}
