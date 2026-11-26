package org.soutalab.celtium.vulkan.bindless;

import java.util.ArrayList;
import java.util.List;

/**
 * The timing half of deferred bindless slot release (S5-1): when a texture is destroyed, its
 * registration is detached from the {@link BindlessTextureTable} immediately - the GL name stops
 * resolving and can be reused - but the slot itself is only freed once the GPU completion value
 * the release was stamped with has passed, because a frame still in flight may sample it.
 *
 * <p>The stamp and the completion value are the renderer's frame currency (in the integrated
 * tree, Stage 2's {@code FrameTimeline}: releases are stamped with the value the current frame
 * will signal and drained against {@code completedValue()}). This class is deliberately
 * currency-agnostic - it compares opaque {@code long} values - so the discipline is testable
 * without a device and the integration only supplies the two numbers.
 *
 * <p>Threading: render thread only, like the table it drains into.
 */
public final class BindlessDeferredReleaseQueue {

    private final List<Integer> handles = new ArrayList<Integer>();
    private final List<Long> retireAtValues = new ArrayList<Long>();

    /**
     * Detaches {@code glId}'s registration from the table and queues its slot for release once
     * {@code completedValue} reaches {@code retireAtValue}. Unknown names queue nothing.
     *
     * @return whether a release was queued
     */
    public boolean defer(BindlessTextureTable table, int glId, long retireAtValue) {
        int handle = table.detachForDeferredRelease(glId);
        if (handle == BindlessTextureTable.INVALID_HANDLE) {
            return false;
        }
        this.handles.add(Integer.valueOf(handle));
        this.retireAtValues.add(Long.valueOf(retireAtValue));
        return true;
    }

    /**
     * Frees every queued slot whose retire value has passed, against the table that detached it.
     * Entries are scanned rather than assumed ordered: stamps are non-decreasing in practice
     * (one currency, issued in frame order), but a scan keeps a late low stamp from stranding
     * behind an early high one if a caller ever stamps out of order.
     *
     * @return how many slots were freed
     */
    public int drain(BindlessTextureTable table, long completedValue) {
        int freed = 0;
        for (int i = this.handles.size() - 1; i >= 0; i--) {
            if (this.retireAtValues.get(i).longValue() <= completedValue) {
                if (table.completeDeferredRelease(this.handles.get(i).intValue())) {
                    freed++;
                }
                this.handles.remove(i);
                this.retireAtValues.remove(i);
            }
        }
        return freed;
    }

    /** Releases still waiting for their completion value. */
    public int pending() {
        return this.handles.size();
    }
}
