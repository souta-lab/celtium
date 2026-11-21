package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;

/**
 * Remembers the push-constant bytes last written for a command buffer, so a draw whose block is unchanged does not
 * push it again.
 * <p>
 * The immediate path pushes the whole 208-byte block for every draw. The dense world scene (qmagnet's exhibition)
 * draws ~920 ITEM batches a frame, and vanilla sets an item's transform once and then draws its quads one after
 * another: those quads carry the same projection, the same model view, the same colour and the same alpha reference,
 * so all but the first push wrote back exactly the bytes already in the command buffer.
 * <p>
 * The cache is keyed by the command buffer <em>and</em> the pipeline layout: push constants are command-buffer state,
 * a fresh command buffer starts with undefined contents, and two layouts may declare the same range with different
 * meanings - so neither the buffer nor the layout alone is enough. A mismatch pushes and re-seeds.
 * <p>
 * Sized to the largest block the immediate families declare (208 bytes); a wider block is pushed unconditionally
 * rather than compared through a truncated copy.
 */
final class VulkanPushCache {
    /**
     * The one cache the immediate and terrain families share.
     * <p>
     * Push constants are command-buffer state keyed by the pipeline <em>layout</em>, not by the pipeline object: two
     * pipelines built over one layout - the unlit and the lit variant of the immediate texture family - write the same
     * range, so a cache per pipeline would let one variant skip a push the other had overwritten. Measured: the first
     * version of this cache was per pipeline and the standard scene's picture changed with it (nonBackgroundFraction
     * 0.9446 -> 0.7802, 17% of pixels differing by more than 8), which is exactly that bug. One tuple for every
     * pusher cannot disagree with itself; when two layouts interleave, the tuple differs and both push.
     */
    static final VulkanPushCache SHARED = new VulkanPushCache();

    /** The command buffer the cached bytes belong to. */
    private long commandBuffer = -1L;
    /** The pipeline layout whose declared range the cached bytes were pushed through. */
    private long pipelineLayout = -1L;
    /** Reusable backing array sized to the largest block seen (starts at 256B, covering every immediate family). */
    private byte[] bytes = new byte[256];
    /** Active byte length in {@link #bytes} for the last remembered push. */
    private int cachedLength;
    /** A view of {@link #bytes} for the bulk comparison, rebuilt only if the backing array grows. */
    private java.nio.ByteBuffer cacheBytesView;

    /** How many pushes were skipped because the block was unchanged. */
    private int skipped;
    /** How many pushes were issued, i.e. the run breaks the elision could not cover. */
    private int issued;

    /**
     * {@return whether the caller has to push {@code data}} - true for a new command buffer, a new layout, a block of a
     * different length, or bytes that differ from the cached ones.
     */
    boolean shouldPush(long commandBuffer, long pipelineLayout, ByteBuffer data) {
        int length = data.remaining();
        // Counted here rather than at the call sites: this is the single place that decides, so the ratio the summary
        // reports is the elision rate of the whole immediate path - and its complement is the number of draw runs an
        // indirect merge would have to emit (a run ends exactly where the block changes).
        if (commandBuffer != this.commandBuffer || pipelineLayout != this.pipelineLayout
                || length != this.cachedLength || length == 0) {
            if (length > 0) {
                this.issued++;
            }
            return true;
        }
        // Vectorised comparison without duplicating or mutating the caller's ByteBuffer: data.remaining() already
        // equals length, and ByteBuffer.mismatch compares [position..limit) directly via ArraysSupport.vectorizedMismatch.
        if (this.cachedBytes(length).mismatch(data) != -1) {
            this.issued++;
            return true;
        }
        this.skipped++;
        return false;
    }

    /**
     * Forgets the cached block. Called after a pipeline whose layout declares no push range was bound (the model
     * instances): push constants are not guaranteed to survive a layout that is incompatible for them, so the next
     * block must be pushed whatever the cache says.
     */
    void invalidate() {
        this.commandBuffer = -1L;
        this.pipelineLayout = -1L;
        this.cachedLength = 0;
    }

    /** Remembers the bytes of a push that was issued, so the next identical block can be elided. */
    void remember(long commandBuffer, long pipelineLayout, ByteBuffer data) {
        int length = data.remaining();
        if (length > this.bytes.length) {
            this.bytes = new byte[length];
            this.cacheBytesView = null;
        }
        // Absolute bulk read into the cache array: leaves data's position untouched and allocates no duplicate view.
        data.get(data.position(), this.bytes, 0, length);
        this.cachedLength = length;
        this.commandBuffer = commandBuffer;
        this.pipelineLayout = pipelineLayout;
    }

    /** {@return the cached bytes as a buffer over the same range, so the comparison is bulk on both sides}. */
    private java.nio.ByteBuffer cachedBytes(int length) {
        if (this.cacheBytesView == null) {
            this.cacheBytesView = java.nio.ByteBuffer.wrap(this.bytes);
        }
        java.nio.ByteBuffer view = this.cacheBytesView;
        view.position(0);
        view.limit(length);
        return view;
    }

    /** {@return how many pushes this cache elided} - the counter the summary reports. */
    int skipped() {
        return this.skipped;
    }

    /** {@return how many pushes were issued}, i.e. how many runs of equal blocks the immediate path walked. */
    int issued() {
        return this.issued;
    }
}
