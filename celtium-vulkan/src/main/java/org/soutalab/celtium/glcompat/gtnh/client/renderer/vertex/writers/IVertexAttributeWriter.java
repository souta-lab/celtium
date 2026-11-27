package org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.writers;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import org.joml.Matrix4fc;
import org.joml.Vector3f;

import org.soutalab.celtium.glcompat.gtnh.client.renderer.DirectTessellator;

public interface IVertexAttributeWriter {

    int writeAttribute(long pointer, int[] data, int index);

    int writeAttribute(long pointer, DirectTessellator tessellator);

    // Populate the Tessellator using the buffer's contents
    int readAttribute(long pointer, DirectTessellator tessellator);

    default int writeAttributeTransformed(long pointer, int[] data, int index, Matrix4fc transform, Vector3f scratch) {
        return writeAttribute(pointer, data, index);
    }
}
