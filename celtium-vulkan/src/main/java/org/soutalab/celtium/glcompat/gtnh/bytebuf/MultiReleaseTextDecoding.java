package org.soutalab.celtium.glcompat.gtnh.bytebuf;

/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import org.soutalab.celtium.glcompat.gtnh.lwjgl2.memory.MemoryUtilities;

import java.nio.ByteBuffer;

/**
 * String decoding utilities.
 *
 * <p>
 * On Java 9 different implementations are used that work better with compat strings (JEP 254).
 * </p>
 */
final class MultiReleaseTextDecoding {

    private MultiReleaseTextDecoding() {}

    public static int classVersion() {
        return Runtime.version().feature() >= 17 ? 17 : 8;
    }

    /** @see MemoryUtilities#memUTF8(ByteBuffer, int, int) */
    static String decodeUTF8(long source, int length) {
        return MemoryUtilities.memUTF8(source, length);
    }
}
