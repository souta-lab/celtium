package org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.writers;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.*;
import static org.soutalab.celtium.glcompat.gtnh.client.renderer.cel.util.ModelQuadUtil.*;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

import org.soutalab.celtium.glcompat.gtnh.client.renderer.DirectTessellator;

public final class PositionVertexAttributeWriter implements IVertexAttributeWriter {

    @Override
    public int writeAttribute(long pointer, int[] data, int index) {
        memPutInt(pointer, data[index | X_INDEX]);
        memPutInt(pointer + 4, data[index | Y_INDEX]);
        memPutInt(pointer + 8, data[index | Z_INDEX]);
        return 12;
    }

    @Override
    public int writeAttributeTransformed(long pointer, int[] data, int index, Matrix4fc m, Vector3f v) {
        v.set(
                Float.intBitsToFloat(data[index | X_INDEX]),
                Float.intBitsToFloat(data[index | Y_INDEX]),
                Float.intBitsToFloat(data[index | Z_INDEX]));
        v.mulPosition(m);
        memPutFloat(pointer, v.x);
        memPutFloat(pointer + 4, v.y);
        memPutFloat(pointer + 8, v.z);
        return 12;
    }

    @Override
    public int writeAttribute(long pointer, DirectTessellator tessellator) {
        throw new UnsupportedOperationException("Cannot write position to tessellator!");
    }

    @Override
    public int readAttribute(long pointer, DirectTessellator tessellator) {
        throw new UnsupportedOperationException("Cannot read position from tessellator!");
    }

}
