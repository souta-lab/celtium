package org.soutalab.celtium.vulkan.terrain;

/**
 * The per-frame terrain upload budget of the new architecture (design section 2.3):
 * all geometry updates flow through one queue at the frame head, capped per frame —
 * 4 MiB on iGPU, 16 MiB on dGPU — with the excess carried to the next frame instead of
 * spiking the frame that happens to receive a chunk burst. Admission is all-or-nothing
 * per upload so a section never lands half-written.
 */
public final class TerrainUploadBudget {
    public static final long IGPU_BYTES_PER_FRAME = 4L * 1024 * 1024;
    public static final long DGPU_BYTES_PER_FRAME = 16L * 1024 * 1024;

    private final long bytesPerFrame;
    private long remainingThisFrame;
    private long admittedThisFrame;
    private long pendingBytes;
    private long pendingUploads;

    public TerrainUploadBudget(long bytesPerFrame) {
        if (bytesPerFrame <= 0) {
            throw new IllegalArgumentException("bytesPerFrame must be positive: " + bytesPerFrame);
        }
        this.bytesPerFrame = bytesPerFrame;
        this.remainingThisFrame = bytesPerFrame;
    }

    public static TerrainUploadBudget forIGpu() {
        return new TerrainUploadBudget(IGPU_BYTES_PER_FRAME);
    }

    public static TerrainUploadBudget forDGpu() {
        return new TerrainUploadBudget(DGPU_BYTES_PER_FRAME);
    }

    /** Opens a new frame: the allowance resets; the pending backlog is unchanged. */
    public void beginFrame() {
        this.remainingThisFrame = this.bytesPerFrame;
        this.admittedThisFrame = 0;
    }

    /**
     * Tries to admit one upload of {@code bytes} into the current frame.
     *
     * @return true when admitted (the allowance is charged); false when it does not fit
     *         and has been accounted into the pending backlog for a later frame
     */
    public boolean tryAdmit(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative: " + bytes);
        }
        if (bytes <= this.remainingThisFrame) {
            this.remainingThisFrame -= bytes;
            this.admittedThisFrame += bytes;
            return true;
        }
        this.pendingBytes += bytes;
        this.pendingUploads++;
        return false;
    }

    /** Marks a previously deferred upload as admitted outside the backlog accounting. */
    public void noteDeferredAdmitted(long bytes) {
        this.pendingBytes = Math.max(0, this.pendingBytes - bytes);
        if (this.pendingUploads > 0) {
            this.pendingUploads--;
        }
    }

    /**
     * Tries to admit an upload that a previous {@link #tryAdmit} deferred: on success
     * the frame allowance is charged and the backlog accounting is settled; on failure
     * nothing changes (the upload stays in the backlog exactly once).
     */
    public boolean tryAdmitDeferred(long bytes) {
        if (bytes > this.remainingThisFrame) {
            return false;
        }
        this.remainingThisFrame -= bytes;
        this.admittedThisFrame += bytes;
        noteDeferredAdmitted(bytes);
        return true;
    }

    public long bytesPerFrame() {
        return this.bytesPerFrame;
    }

    public long remainingThisFrame() {
        return this.remainingThisFrame;
    }

    public long admittedThisFrame() {
        return this.admittedThisFrame;
    }

    public long pendingBytes() {
        return this.pendingBytes;
    }

    public long pendingUploads() {
        return this.pendingUploads;
    }

    /** Whole frames the current backlog needs at the per-frame rate, rounded up. */
    public long framesToDrain() {
        if (this.pendingBytes == 0) {
            return 0;
        }
        return (this.pendingBytes + this.bytesPerFrame - 1) / this.bytesPerFrame;
    }
}
