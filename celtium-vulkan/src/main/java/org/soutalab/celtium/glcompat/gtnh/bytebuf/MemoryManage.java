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


import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.MemoryAllocator;

import org.soutalab.celtium.glcompat.gtnh.lwjgl2.memory.MemoryUtilities;

/** Provides {@link MemoryAllocator} implementations for {@link MemoryUtilities} to use. */
final class MemoryManage {

    private MemoryManage() {}

    static MemoryAllocator getInstance() {
        return new LwjglAllocator();
    }

    /** Adapts LWJGL's maintained native allocator to the local public allocator contract. */
    private static final class LwjglAllocator implements MemoryAllocator {

        @Override
        public long malloc(long size) {
            return MemoryUtilities.nmemAlloc(size);
        }

        @Override
        public long calloc(long num, long size) {
            return MemoryUtilities.nmemCalloc(1L, Math.multiplyExact(num, size));
        }

        @Override
        public long realloc(long ptr, long size) {
            return MemoryUtilities.nmemRealloc(ptr, size);
        }

        @Override
        public void free(long ptr) {
            MemoryUtilities.nmemFree(ptr);
        }
    }

}
