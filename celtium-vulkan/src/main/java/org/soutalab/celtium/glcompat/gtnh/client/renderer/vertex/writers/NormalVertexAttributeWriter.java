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

public final class NormalVertexAttributeWriter implements IVertexAttributeWriter {

    @Override
    public int writeAttribute(long pointer, int[] data, int index) {
        memPutInt(pointer, data[index | NORMAL_INDEX]);
        return 4;
    }

    @Override
    public int writeAttributeTransformed(long pointer, int[] data, int index, Matrix4fc m, Vector3f n) {
        int packed = data[index | NORMAL_INDEX];
        n.set((byte) (packed) / 127f, (byte) (packed >> 8) / 127f, (byte) (packed >> 16) / 127f);
        n.mulDirection(m).normalize();

        memPutInt(
                pointer,
                ((byte) (n.x * 127)) & 0xFF | (((byte) (n.y * 127)) & 0xFF) << 8 | (((byte) (n.z * 127)) & 0xFF) << 16);
        return 4;
    }

    @Override
    public int writeAttribute(long pointer, DirectTessellator tessellator) {
        memPutInt(pointer, tessellator.getPackedNormal());
        return 4;
    }

    @Override
    public int readAttribute(long pointer, DirectTessellator tessellator) {
        tessellator.setPackedNormalRaw(memGetInt(pointer));
        return 4;
    }
}
