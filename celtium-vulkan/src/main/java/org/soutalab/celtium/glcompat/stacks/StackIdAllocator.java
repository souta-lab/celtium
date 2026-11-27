package org.soutalab.celtium.glcompat.stacks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


public final class StackIdAllocator {
    private static final int STATIC_BASE = 4096;
    private static final int STATIC_CAPACITY = 16;

    private static int next = -1;
    private static int perContext = -1;
    private static int staticNext;

    private StackIdAllocator() {}

    public static void beginContext() {
        next = 0;
    }

    public static void endContext() {
        if (perContext == -1) {
            perContext = next;
        } else if (next != perContext) {
            throw new IllegalStateException("stack id count changed across contexts: " + perContext + " -> " + next);
        }
        next = -1;
    }

    public static int nextId() {
        return next >= 0 ? next++ : -1;
    }

    public static int nextStaticId() {
        if (staticNext >= STATIC_CAPACITY) {
            throw new IllegalStateException("static stack id capacity exceeded");
        }
        return STATIC_BASE + staticNext++;
    }

    public static int capacity() {
        return STATIC_BASE + STATIC_CAPACITY;
    }
}
