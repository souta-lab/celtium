package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;

/**
 * A reusable limited view of a caller's push-constant buffer.
 * <p>
 * The immediate families each need the block clamped to the bytes their pipeline layout declares, and the obvious way
 * to get it - {@code pushConstants.duplicate()} - allocates a direct buffer and registers it with a cleaner, once per
 * packet (~172 a frame). This keeps one per source buffer instead.
 * <p>
 * <b>Measured, and it is not a speedup.</b> The packet split named 5.5 us of an 11.5 us packet as neither the push,
 * the binds nor the draw call, and a per-packet direct-buffer duplicate was the obvious suspect; removing it moved the
 * packet from 11.47 to 11.41 us and the frame not at all. So the allocation is gone and the cause of that 5.5 us is
 * still unidentified - do not cite this class as an optimisation.
 * <p>
 * The caller hands the same buffer in for every group of a flush, so the duplicate is kept for as long as that remains
 * true; a different buffer replaces it. Position and limit are re-read from the source on every call, because the
 * flush rewrites the same block in place between groups and a stale window would push the previous group's bytes.
 */
final class ReusableDuplicate {

    private ByteBuffer view;
    private ByteBuffer source;

    /**
     * {@return a view over {@code original}'s current range, clamped to {@code maxBytes}}, reusing the previous
     * duplicate when the source has not changed. The returned buffer must be consumed before the next call.
     */
    ByteBuffer view(ByteBuffer original, int maxBytes) {
        if (this.view == null || this.source != original) {
            this.view = original.duplicate();
            this.source = original;
        }
        // Absolute positions, not remaining(): the old form set the limit from the remaining count, which is only the
        // same number while the block sits at position 0 - and wrong (or an exception) when it does not.
        int start = original.position();
        int end = Math.min(original.limit(), start + maxBytes);
        this.view.position(start).limit(end);
        return this.view;
    }
}
