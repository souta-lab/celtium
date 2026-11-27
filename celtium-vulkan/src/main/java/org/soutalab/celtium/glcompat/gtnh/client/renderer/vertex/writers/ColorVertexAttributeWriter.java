package org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.writers;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.*;
import static org.soutalab.celtium.glcompat.gtnh.client.renderer.cel.util.ModelQuadUtil.*;

import org.soutalab.celtium.glcompat.gtnh.client.renderer.DirectTessellator;

public final class ColorVertexAttributeWriter implements IVertexAttributeWriter {

    @Override
    public int writeAttribute(long pointer, int[] data, int index) {
        memPutInt(pointer, data[index | COLOR_INDEX]);
        return 4;
    }

    @Override
    public int writeAttribute(long pointer, DirectTessellator tessellator) {
        memPutInt(pointer, tessellator.getPackedColor());
        return 4;
    }

    @Override
    public int readAttribute(long pointer, DirectTessellator tessellator) {
        tessellator.setPackedColorRaw(memGetInt(pointer));
        return 4;
    }
}
