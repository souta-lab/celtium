package org.soutalab.celtium.glcompat.gtnh.lwjgl2.memory;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import org.lwjgl.PointerBuffer;

import java.nio.ByteBuffer;

/** Bridges the incompatible PointerBuffer APIs exposed by LWJGL2 and LWJGL3. */
final class PointerBufferCompat {
    private PointerBufferCompat() {
    }

    static PointerBuffer create(ByteBuffer buffer) {
        try {
            return (PointerBuffer) PointerBuffer.class.getMethod("create", ByteBuffer.class).invoke(null, buffer);
        } catch (NoSuchMethodException legacyApi) {
            try {
                return PointerBuffer.class.getConstructor(ByteBuffer.class).newInstance(buffer);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not create a PointerBuffer", failure);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not create a PointerBuffer", failure);
        }
    }

    static long baseAddress(PointerBuffer buffer) {
        try {
            return (long) PointerBuffer.class.getMethod("address").invoke(buffer);
        } catch (NoSuchMethodException legacyApi) {
            try {
                ByteBuffer bytes = (ByteBuffer) PointerBuffer.class.getMethod("getBuffer").invoke(buffer);
                return MemoryUtilities.memAddress0(bytes);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not read a PointerBuffer address", failure);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read a PointerBuffer address", failure);
        }
    }
}
