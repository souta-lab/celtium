package org.soutalab.celtium.vulkan;

/**
 * The texture upload service: one shared staging pool and one batch transfer queue for every
 * texture upload, replacing the per-texture staging buffers and per-texture submit-and-drain
 * uploads {@code VulkanGlTexture} performs today (design OVERHAUL-S3-REMAINDER-2026-11-26
 * &sect;3.2). The read-back direction is not this class's business: the existing
 * {@code TextureCopyService} keeps {@code glCopyTexImage} copies, and the plan's M11 wording
 * naming that class for the upload side is disposed of in the design record - the upload side
 * lives here so the two directions never share one owner.
 * <p>
 * <b>State of this class.</b> This is the landing-A skeleton: the queue, the flush policy and
 * the counters are complete and unit-tested, but nothing calls the service yet. The remaining
 * landing-A work is wiring, and it is deliberately not faked here:
 * <ul>
 *   <li>{@code VulkanContext} becomes the owner and supplies the {@link BatchSubmitter}: record
 *   the batch's layout transitions and buffer-to-image copies into one command buffer and hand
 *   it to the context's deferred-submit mechanism, returning the frame timeline value the batch
 *   completes at. The submitter also performs the actual staging writes (each request names a
 *   native source address; the bytes are copied into the slice the batch resolved for it).</li>
 *   <li>The staging backing is the context's host-visible slab pool once the S34 tree lands;
 *   {@link TextureStagingPool} speaks only in offsets, so the binding does not change this
 *   class.</li>
 *   <li>{@code VulkanGlTexture}'s full-level upload path switches to {@link #enqueue}; its patch
 *   path and its per-texture staging fields follow in landings B and C.</li>
 *   <li>The three counters below are spliced into {@code BackendDiagnostics} and
 *   {@code docs/instruments.md} is regenerated in the same diff that wires the owner.</li>
 * </ul>
 * <b>Flush policy.</b> {@link #flush(long, BatchSubmitter)} drains the queue in strict FIFO
 * order into as many batches as the pool's reusable space requires. Order is never rearranged:
 * uploads to one image (its levels, then its patches) must land in issue order, and a global
 * FIFO is the simplest form that guarantees it. A batch ends at the first request whose slice
 * does not fit the space reusable <em>now</em>. What happens then depends on the head request's
 * class:
 * <ul>
 *   <li><b>Deferrable</b> (a texture's first, full-level upload at load time): the flush stops.
 *   The request and everything behind it stay queued and their bytes are counted as deferred.
 *   Load-time uploads may arrive a frame later; that is the upload budget's deferral point.</li>
 *   <li><b>Immediate</b> (dynamic texture updates and patches - animated sprites, the Xaero
 *   minimap): the flush also stops, but reports {@link FlushResult#blockedOnValue()}: the
 *   timeline value whose completion frees the oldest staging data. The owner awaits that value
 *   on the frame timeline and calls flush again. An immediate upload is never silently deferred
 *   past a frame boundary, because a stale minimap or a frozen animation is a visible defect,
 *   not a load-time tail.</li>
 * </ul>
 * A single request larger than the pool is refused at {@link #enqueue} - the caller keeps the
 * legacy per-texture path for it - rather than deadlocking every flush behind it.
 * <p>
 * Not thread-safe: the queue is written by the one thread recording uploads, and flush runs on
 * that same thread.
 */
public final class TextureUploadService {

    /** Whether an upload may wait for staging space or must be submitted in this flush. */
    public enum Urgency {
        /** First, full-level uploads at load time; may be deferred by staging pressure. */
        DEFERRABLE,
        /** Dynamic updates and patches; the flush reports what to await instead of deferring. */
        IMMEDIATE
    }

    /** One copy region of an upload: the sub-rectangle of one mip level of one layer. */
    public static final class Region {
        public final int mipLevel;
        public final int baseLayer;
        public final int layerCount;
        public final int x;
        public final int y;
        public final int z;
        public final int width;
        public final int height;
        public final int depth;

        public Region(int mipLevel, int baseLayer, int layerCount,
                      int x, int y, int z, int width, int height, int depth) {
            if (mipLevel < 0 || baseLayer < 0 || layerCount <= 0) {
                throw new IllegalArgumentException("region level/layers out of range: level " + mipLevel
                        + ", baseLayer " + baseLayer + ", layerCount " + layerCount);
            }
            if (x < 0 || y < 0 || z < 0 || width <= 0 || height <= 0 || depth <= 0) {
                throw new IllegalArgumentException("region extent out of range: (" + x + "," + y + "," + z
                        + ") " + width + "x" + height + "x" + depth);
            }
            this.mipLevel = mipLevel;
            this.baseLayer = baseLayer;
            this.layerCount = layerCount;
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }

    /**
     * One queued upload: the pixel bytes at {@code sourceAddress} (a native address owned by
     * the caller until the batch that carries this request is submitted) go to {@code region} of
     * {@code image}, moving the image from {@code oldLayout} to {@code newLayout}, optionally
     * followed by mip generation over the uploaded levels. The layouts are raw
     * {@code VkImageLayout} values; this class never interprets them, it hands them to the
     * submitter with the request.
     */
    public static final class UploadRequest {
        public final long image;
        public final int oldLayout;
        public final int newLayout;
        public final boolean generateMips;
        public final Urgency urgency;
        public final Region region;
        public final long sourceAddress;
        public final long byteCount;

        public UploadRequest(long image, int oldLayout, int newLayout, boolean generateMips,
                             Urgency urgency, Region region, long sourceAddress, long byteCount) {
            if (image == 0L) {
                throw new IllegalArgumentException("upload image must not be VK_NULL_HANDLE");
            }
            if (urgency == null) {
                throw new IllegalArgumentException("upload urgency must not be null");
            }
            if (region == null) {
                throw new IllegalArgumentException("upload region must not be null");
            }
            if (sourceAddress == 0L) {
                throw new IllegalArgumentException("upload source address must not be null");
            }
            if (byteCount <= 0L) {
                throw new IllegalArgumentException("upload byte count must be positive, was " + byteCount);
            }
            this.image = image;
            this.oldLayout = oldLayout;
            this.newLayout = newLayout;
            this.generateMips = generateMips;
            this.urgency = urgency;
            this.region = region;
            this.sourceAddress = sourceAddress;
            this.byteCount = byteCount;
        }
    }

    /** A request paired with the staging slice the flush resolved for it. */
    public static final class ResolvedUpload {
        public final UploadRequest request;
        public final long stagingOffset;

        public ResolvedUpload(UploadRequest request, long stagingOffset) {
            this.request = request;
            this.stagingOffset = stagingOffset;
        }
    }

    /**
     * The device seam of a flush: record one batch and submit it. Implementations write each
     * request's bytes into its resolved staging slice, record the layout transitions and
     * buffer-to-image copies (and mip generation where asked) into one command buffer, and
     * submit it through the context's deferred-submit mechanism.
     *
     * @return the frame timeline value the batch completes at; the pool retires the batch's
     *         slices when the completed value reaches it
     */
    public interface BatchSubmitter {
        long submit(java.util.List<ResolvedUpload> batch);
    }

    /** What one {@link #flush(long, BatchSubmitter)} call did. */
    public static final class FlushResult {
        private final int batchesSubmitted;
        private final int requestsSubmitted;
        private final long bytesSubmitted;
        private final int deferredRequests;
        private final long deferredBytes;
        private final long blockedOnValue;

        FlushResult(int batchesSubmitted, int requestsSubmitted, long bytesSubmitted,
                    int deferredRequests, long deferredBytes, long blockedOnValue) {
            this.batchesSubmitted = batchesSubmitted;
            this.requestsSubmitted = requestsSubmitted;
            this.bytesSubmitted = bytesSubmitted;
            this.deferredRequests = deferredRequests;
            this.deferredBytes = deferredBytes;
            this.blockedOnValue = blockedOnValue;
        }

        /** {@return how many batches this flush submitted}. */
        public int batchesSubmitted() {
            return this.batchesSubmitted;
        }

        /** {@return how many queued requests this flush submitted}. */
        public int requestsSubmitted() {
            return this.requestsSubmitted;
        }

        /** {@return the pixel bytes this flush submitted}. */
        public long bytesSubmitted() {
            return this.bytesSubmitted;
        }

        /** {@return how many requests this flush left queued behind a deferrable head}. */
        public int deferredRequests() {
            return this.deferredRequests;
        }

        /** {@return the pixel bytes this flush left queued behind a deferrable head}. */
        public long deferredBytes() {
            return this.deferredBytes;
        }

        /**
         * {@return the timeline value whose completion frees staging space for the immediate
         * head this flush could not place, or {@code 0} when the flush was not blocked on an
         * immediate request}. The owner awaits this value and flushes again.
         */
        public long blockedOnValue() {
            return this.blockedOnValue;
        }
    }

    private final TextureStagingPool pool;
    private final java.util.ArrayDeque<UploadRequest> queue = new java.util.ArrayDeque<UploadRequest>();
    private long pendingBytes;
    private long submits;
    private long uploadedBytes;
    private long deferredBytesTotal;

    /**
     * @param stagingCapacityBytes the shared staging pool's fixed size
     * @param stagingAlignment     the alignment every staging offset satisfies (16 matches the
     *                             per-texture staging cursor {@code VulkanGlTexture} keeps today)
     */
    public TextureUploadService(long stagingCapacityBytes, long stagingAlignment) {
        this.pool = new TextureStagingPool(stagingCapacityBytes, stagingAlignment);
    }

    /** {@return the staging pool this service hands slices out of}. */
    public TextureStagingPool stagingPool() {
        return this.pool;
    }

    /**
     * Queues one upload behind everything already queued.
     *
     * @throws IllegalArgumentException when the request alone exceeds the staging pool - the
     *         caller keeps the legacy per-texture path for such an upload instead of the queue
     *         deadlocking behind a request that can never fit
     */
    public void enqueue(UploadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("upload request must not be null");
        }
        if (request.byteCount > this.pool.capacity()) {
            throw new IllegalArgumentException("upload of " + request.byteCount
                    + " bytes exceeds the staging pool capacity of " + this.pool.capacity()
                    + " bytes; the caller must use a dedicated path for it");
        }
        this.queue.addLast(request);
        this.pendingBytes += request.byteCount;
    }

    /** {@return how many uploads are queued}. */
    public int pendingRequests() {
        return this.queue.size();
    }

    /** {@return the pixel bytes queued}. */
    public long pendingBytes() {
        return this.pendingBytes;
    }

    /**
     * Retires the staging slices {@code completedValue} has completed, then submits the queue
     * in FIFO batches as far as the reusable space reaches, under the class-level flush policy.
     *
     * @param completedValue the frame timeline's completed value, from device evidence
     * @param submitter      the batch recording/submission seam
     */
    public FlushResult flush(long completedValue, BatchSubmitter submitter) {
        if (submitter == null) {
            throw new IllegalArgumentException("batch submitter must not be null");
        }
        this.pool.releaseCompleted(completedValue);
        int batches = 0;
        int requests = 0;
        long bytes = 0L;
        int deferredRequests = 0;
        long deferredBytes = 0L;
        long blockedOn = 0L;
        while (!this.queue.isEmpty()) {
            java.util.ArrayList<ResolvedUpload> batch = new java.util.ArrayList<ResolvedUpload>();
            long batchBytes = 0L;
            for (UploadRequest request : this.queue) {
                long offset = this.pool.allocate(request.byteCount);
                if (offset < 0L) {
                    break;
                }
                batch.add(new ResolvedUpload(request, offset));
                batchBytes += request.byteCount;
            }
            if (batch.isEmpty()) {
                UploadRequest head = this.queue.peekFirst();
                if (head.urgency == Urgency.DEFERRABLE) {
                    deferredRequests = this.queue.size();
                    deferredBytes = this.pendingBytes;
                    this.deferredBytesTotal += deferredBytes;
                } else {
                    blockedOn = this.pool.oldestInFlightValue();
                    if (blockedOn == 0L) {
                        // Nothing is in flight, so waiting cannot free space: with the enqueue
                        // capacity check this is unreachable unless alignment waste strands a
                        // request, and that is a sizing bug to name, not a state to wait in.
                        throw new IllegalStateException("immediate upload of " + head.byteCount
                                + " bytes does not fit the staging pool (capacity "
                                + this.pool.capacity() + ", largest free run "
                                + this.pool.largestFreeRun() + ") with nothing in flight");
                    }
                }
                break;
            }
            long stamp = submitter.submit(java.util.Collections.unmodifiableList(batch));
            for (ResolvedUpload resolved : batch) {
                this.pool.markSubmitted(resolved.stagingOffset, stamp);
                this.queue.pollFirst();
                this.pendingBytes -= resolved.request.byteCount;
            }
            batches++;
            requests += batch.size();
            bytes += batchBytes;
            this.submits++;
            this.uploadedBytes += batchBytes;
        }
        return new FlushResult(batches, requests, bytes, deferredRequests, deferredBytes, blockedOn);
    }

    /** {@return batches submitted since creation - the {@code textureUploadSubmits} instrument}. */
    public long submits() {
        return this.submits;
    }

    /** {@return pixel bytes submitted since creation - the {@code textureUploadBytes} instrument}. */
    public long uploadedBytes() {
        return this.uploadedBytes;
    }

    /**
     * {@return pixel bytes left queued by deferral, summed over flush calls - the
     * {@code textureUploadDeferredBytes} instrument}. A request deferred by two flush calls
     * counts in both: the instrument measures deferral pressure, not distinct requests.
     */
    public long deferredBytesTotal() {
        return this.deferredBytesTotal;
    }
}
