package org.soutalab.celtium.vulkan.upload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Unified frame-head transfer queue for P0 ({@code UploadBatch}) in the new rendering architecture
 * (spec §2.1 &amp; §2.3, Module Migration Map M05/M11, E9/F2 animated-sprite rework).
 *
 * <p>Key invariants:
 * <ul>
 *   <li>All buffer (terrain slab / dynamic) and image (atlas / animated sprite) uploads are
 *       recorded together at the start of the frame so P0 ends with a single transfer-to-graphics
 *       barrier.</li>
 *   <li>A per-frame byte budget ({@link #IGPU_BUDGET_BYTES} = 4 MiB on iGPU,
 *       {@link #DGPU_BUDGET_BYTES} = 16 MiB on dGPU) smooths chunk-streaming bursts; excess items
 *       carry over to the next frame without blocking.</li>
 *   <li>{@link UploadRequest.Priority#IMMEDIATE} requests always drain before
 *       {@link UploadRequest.Priority#BACKGROUND}.</li>
 *   <li>Repeated pending updates to the exact same image sub-rectangle (animated sprites, E9/F2)
 *       coalesce in-place so only the latest staging bytes are uploaded.</li>
 *   <li>Drained image copies are grouped by destination image handle so each touched texture pays
 *       at most one {@code -> TRANSFER_DST} and one {@code -> SHADER_READ_ONLY} layout transition
 *       per frame.</li>
 * </ul>
 */
public final class UploadQueue {

    /** Spec §2.3 default per-frame upload budget for integrated GPUs (UHD 630 / HD 630). */
    public static final int IGPU_BUDGET_BYTES = 4 * 1024 * 1024;

    /** Spec §2.3 default per-frame upload budget for discrete GPUs (RX 6400 / GTX 1650 Ti). */
    public static final int DGPU_BUDGET_BYTES = 16 * 1024 * 1024;

    private final int frameBudgetBytes;
    private final Deque<UploadRequest> immediateQueue = new ArrayDeque<>();
    private final Deque<UploadRequest> backgroundQueue = new ArrayDeque<>();
    private long totalCoalescedSprites;
    private long totalBytesUploaded;
    private long totalFramesDrained;

    public UploadQueue(int frameBudgetBytes) {
        if (frameBudgetBytes <= 0) {
            throw new IllegalArgumentException("frameBudgetBytes must be positive: " + frameBudgetBytes);
        }
        this.frameBudgetBytes = frameBudgetBytes;
    }

    public static UploadQueue forIntegratedGpu() {
        return new UploadQueue(IGPU_BUDGET_BYTES);
    }

    public static UploadQueue forDiscreteGpu() {
        return new UploadQueue(DGPU_BUDGET_BYTES);
    }

    /**
     * Enqueues a transfer request. If it is an {@link UploadRequest.Kind#IMAGE_SUBRECT_COPY}
     * targeting the exact same sub-rectangle as an already-pending request in the same priority
     * lane, the older pending request is replaced (coalesced) and {@code true} is returned.
     */
    public boolean enqueue(UploadRequest request) {
        Objects.requireNonNull(request, "request");
        Deque<UploadRequest> target = (request.priority() == UploadRequest.Priority.IMMEDIATE)
                ? immediateQueue
                : backgroundQueue;

        if (request.kind() == UploadRequest.Kind.IMAGE_SUBRECT_COPY && !target.isEmpty()) {
            boolean replaced = replaceMatchingSubrect(target, request);
            if (replaced) {
                totalCoalescedSprites++;
                return true;
            }
        }
        target.addLast(request);
        return false;
    }

    private static boolean replaceMatchingSubrect(Deque<UploadRequest> queue, UploadRequest replacement) {
        long key = replacement.coalescingKey();
        List<UploadRequest> scratch = null;
        boolean found = false;
        for (UploadRequest existing : queue) {
            if (existing.coalescingKey() == key && existing.sameImageSubrect(replacement)) {
                found = true;
                break;
            }
        }
        if (!found) {
            return false;
        }
        scratch = new ArrayList<>(queue.size());
        while (!queue.isEmpty()) {
            UploadRequest item = queue.removeFirst();
            if (item.coalescingKey() == key && item.sameImageSubrect(replacement)) {
                scratch.add(replacement);
            } else {
                scratch.add(item);
            }
        }
        for (UploadRequest item : scratch) {
            queue.addLast(item);
        }
        return true;
    }

    /**
     * Drains up to {@link #frameBudgetBytes()} of pending transfers into {@code sink}, grouping
     * image copies by destination image handle and emitting a single trailing
     * {@link CommandSink#transferToGraphicsBarrier()} when at least one transfer was recorded.
     */
    public FrameUploadReport drainFrame(CommandSink sink) {
        Objects.requireNonNull(sink, "sink");

        List<UploadRequest> bufferCopies = new ArrayList<>();
        Map<Long, List<UploadRequest>> imageCopiesByHandle = new LinkedHashMap<>();

        int spentBytes = 0;
        boolean budgetExhausted = false;

        budgetExhausted |= drainLane(immediateQueue, bufferCopies, imageCopiesByHandle, spentBytes);
        spentBytes = sumBytes(bufferCopies, imageCopiesByHandle);

        if (!budgetExhausted) {
            budgetExhausted |= drainLane(backgroundQueue, bufferCopies, imageCopiesByHandle, spentBytes);
            spentBytes = sumBytes(bufferCopies, imageCopiesByHandle);
        }

        int bufferCount = bufferCopies.size();
        int imageCount = 0;

        for (int i = 0; i < bufferCount; i++) {
            UploadRequest req = bufferCopies.get(i);
            sink.copyBuffer(req.stagingOffset(), req.dstHandle(), req.dstOffset(), req.sizeBytes());
        }

        for (Map.Entry<Long, List<UploadRequest>> entry : imageCopiesByHandle.entrySet()) {
            long imageHandle = entry.getKey();
            List<UploadRequest> rects = entry.getValue();
            sink.transitionImageToTransferDst(imageHandle);
            for (int i = 0; i < rects.size(); i++) {
                UploadRequest r = rects.get(i);
                sink.copyBufferToImage(
                        r.stagingOffset(),
                        imageHandle,
                        r.mipLevel(),
                        r.x(),
                        r.y(),
                        r.width(),
                        r.height(),
                        r.sizeBytes());
                imageCount++;
            }
            sink.transitionImageToShaderRead(imageHandle);
        }

        if (bufferCount > 0 || imageCount > 0) {
            sink.transferToGraphicsBarrier();
        }

        totalBytesUploaded += spentBytes;
        totalFramesDrained++;

        return new FrameUploadReport(
                spentBytes,
                bufferCount,
                imageCount,
                imageCopiesByHandle.size(),
                pendingCount(),
                budgetExhausted);
    }

    private boolean drainLane(Deque<UploadRequest> lane,
                              List<UploadRequest> bufferCopies,
                              Map<Long, List<UploadRequest>> imageCopiesByHandle,
                              int alreadySpent) {
        int spent = alreadySpent;
        while (!lane.isEmpty()) {
            UploadRequest next = lane.peekFirst();
            int size = next.sizeBytes();
            // Anti-starvation: if nothing has been uploaded yet this frame, admit an oversized item.
            if (spent > 0 && spent + size > frameBudgetBytes) {
                return true;
            }
            lane.removeFirst();
            spent += size;
            if (next.kind() == UploadRequest.Kind.BUFFER_COPY) {
                bufferCopies.add(next);
            } else {
                imageCopiesByHandle
                        .computeIfAbsent(next.dstHandle(), k -> new ArrayList<>())
                        .add(next);
            }
            if (spent >= frameBudgetBytes && !lane.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int sumBytes(List<UploadRequest> buffers, Map<Long, List<UploadRequest>> images) {
        int total = 0;
        for (int i = 0; i < buffers.size(); i++) {
            total += buffers.get(i).sizeBytes();
        }
        for (List<UploadRequest> list : images.values()) {
            for (int i = 0; i < list.size(); i++) {
                total += list.get(i).sizeBytes();
            }
        }
        return total;
    }

    public int frameBudgetBytes() {
        return frameBudgetBytes;
    }

    public int pendingImmediateCount() {
        return immediateQueue.size();
    }

    public int pendingBackgroundCount() {
        return backgroundQueue.size();
    }

    public int pendingCount() {
        return immediateQueue.size() + backgroundQueue.size();
    }

    public long totalCoalescedSprites() {
        return totalCoalescedSprites;
    }

    public long totalBytesUploaded() {
        return totalBytesUploaded;
    }

    public long totalFramesDrained() {
        return totalFramesDrained;
    }

    /** Summary of one P0 {@code UploadBatch} drain. */
    public static final class FrameUploadReport {
        private final int bytesUploaded;
        private final int bufferCopies;
        private final int imageCopies;
        private final int distinctImagesTouched;
        private final int deferredRemaining;
        private final boolean budgetExhausted;

        public FrameUploadReport(int bytesUploaded,
                                 int bufferCopies,
                                 int imageCopies,
                                 int distinctImagesTouched,
                                 int deferredRemaining,
                                 boolean budgetExhausted) {
            this.bytesUploaded = bytesUploaded;
            this.bufferCopies = bufferCopies;
            this.imageCopies = imageCopies;
            this.distinctImagesTouched = distinctImagesTouched;
            this.deferredRemaining = deferredRemaining;
            this.budgetExhausted = budgetExhausted;
        }

        public int bytesUploaded() {
            return bytesUploaded;
        }

        public int bufferCopies() {
            return bufferCopies;
        }

        public int imageCopies() {
            return imageCopies;
        }

        public int distinctImagesTouched() {
            return distinctImagesTouched;
        }

        public int deferredRemaining() {
            return deferredRemaining;
        }

        public boolean budgetExhausted() {
            return budgetExhausted;
        }
    }

    /** Thin Vulkan command sink for P0 {@code UploadBatch} recording. */
    public interface CommandSink {
        void copyBuffer(long stagingOffset, long dstBufferHandle, long dstOffset, int sizeBytes);

        void transitionImageToTransferDst(long imageHandle);

        void copyBufferToImage(long stagingOffset,
                               long dstImageHandle,
                               int mipLevel,
                               int x,
                               int y,
                               int width,
                               int height,
                               int sizeBytes);

        void transitionImageToShaderRead(long imageHandle);

        /** Single P0 -> P1/P2 transfer-write to shader/vertex/indirect-read barrier. */
        void transferToGraphicsBarrier();
    }
}
