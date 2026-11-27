package org.soutalab.celtium.glcompat.stacks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/**
 * Identifiers of the state stacks a caller touched inside one attribute push, so the same delta can be
 * replayed after the push has already been popped.
 *
 * <p>Retained state lets a renderer save a small state delta, restore the attribute stack (returning the
 * driver and the cache to their entry values), and later apply the delta again without keeping its own
 * copy of every tracked value. {@code GLStateManager.retainModifiedState} fills it from the modified-stack
 * list of the open push; {@code applyRetainedState} replays only the stacks whose retained slot still
 * differs from the live value.
 */
public final class RetainedState {
    /** Stack ids in capture order; the array grows to hold the modified count of the widest push. */
    public int[] ids = new int[64];
    /** Number of valid entries in {@link #ids}. */
    public int count;
    /** Program bound when the delta was captured, re-applied only while its lifetime generation matches. */
    public int program;
    /** {@code GLStateManager.programLifetimeGeneration} at capture time; guards {@link #program} against id reuse. */
    public long programGen;

    public void ensureCapacity(int capacity) {
        if (ids.length < capacity) ids = new int[capacity];
    }
}
