/*
 * Copyright LWJGL. All rights reserved. License terms: https://www.lwjgl.org/license
 */
package org.soutalab.celtium.glcompat.gtnh.bytebuf;

/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import org.soutalab.celtium.glcompat.gtnh.lwjgl2.memory.MemoryUtilities;

final class MultiReleaseMemCopy {

    private MultiReleaseMemCopy() {}

    public static int classVersion() {
        return Runtime.version().feature() >= 17 ? 17 : 8;
    }

    static void copy(long src, long dst, long bytes) {
        MemoryUtilities.memCopy(src, dst, bytes);
    }

}
