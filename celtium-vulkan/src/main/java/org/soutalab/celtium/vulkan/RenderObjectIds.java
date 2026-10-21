package org.soutalab.celtium.vulkan;

import java.util.concurrent.atomic.AtomicInteger;

/** Shared opaque identities for native resources and the mod compatibility namespace. Does not create GL objects. */
public final class RenderObjectIds {
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private RenderObjectIds() {
    }

    public static int reserve(int count) {
        if (count <= 0) throw new IllegalArgumentException("positive identity range required");
        return NEXT.getAndUpdate(first -> {
            if (first > Integer.MAX_VALUE - count) throw new IllegalStateException("renderer object identities exhausted");
            return first + count;
        });
    }
}
