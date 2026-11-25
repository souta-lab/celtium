package org.soutalab.celtium.vulkan.terrain;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Per-frame byte-budgeted terrain upload queue with 2-tier priority (Immediate / Background)
 * and automatic carry-over to subsequent frames ({@code CELTIUM-NEW-ARCH-2026-11-25.md} §2.3–2.4,
 * {@code SECTION-DESC-SPEC-2026-11-25.md} Step 1).
 *
 * <p>Key invariants:
 * <ul>
 *   <li>iGPU default budget is 4 MiB/frame; dGPU default is 16 MiB/frame.</li>
 *   <li>{@link Priority#IMMEDIATE} entries (player edits within 3 chunks) always drain before
 *       {@link Priority#BACKGROUND} entries (flight horizon loads).</li>
 *   <li>Superseding a pending upload for the same {@code sectionPassKey} replaces the older payload
 *       immediately and invokes its {@link AutoCloseable#close()} release hook so off-heap staging
 *       buffers are never leaked or read after free.</li>
 *   <li>Forward-progress guarantee: if the queue is non-empty at the start of a frame and the head
 *       item alone exceeds {@code maxBytesPerFrame}, that single item is still admitted so oversized
 *       sections never starve indefinitely.</li>
 * </ul>
 */
public final class TerrainUploadBudget<T extends AutoCloseable> {
    public static final int DEFAULT_IGPU_BUDGET_BYTES = 4 * 1024 * 1024;
    public static final int DEFAULT_DGPU_BUDGET_BYTES = 16 * 1024 * 1024;

    public enum Priority {
        IMMEDIATE,
        BACKGROUND
    }

    public record Entry<T extends AutoCloseable>(
            long sectionPassKey,
            int byteSize,
            Priority priority,
            int generation,
            T payload
    ) {
        public Entry {
            if (byteSize < 0) {
                throw new IllegalArgumentException("Negative byteSize: " + byteSize);
            }
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(payload, "payload");
        }
    }

    public record DrainStats(
            int admittedCount,
            long admittedBytes,
            int remainingCount,
            long remainingBytes
    ) {
    }

    private final int maxBytesPerFrame;
    private final ArrayDeque<Long> immediateOrder = new ArrayDeque<>();
    private final ArrayDeque<Long> backgroundOrder = new ArrayDeque<>();
    private final HashMap<Long, Entry<T>> pendingByKey = new HashMap<>();

    private long pendingBytes;
    private long totalAdmittedCount;
    private long totalAdmittedBytes;
    private long totalSupersededCount;
    private long totalDeferredFrames;

    public TerrainUploadBudget(int maxBytesPerFrame) {
        if (maxBytesPerFrame <= 0) {
            throw new IllegalArgumentException("maxBytesPerFrame must be > 0, got " + maxBytesPerFrame);
        }
        this.maxBytesPerFrame = maxBytesPerFrame;
    }

    public static <T extends AutoCloseable> TerrainUploadBudget<T> forIntegratedGpu() {
        return new TerrainUploadBudget<>(DEFAULT_IGPU_BUDGET_BYTES);
    }

    public static <T extends AutoCloseable> TerrainUploadBudget<T> forDiscreteGpu() {
        return new TerrainUploadBudget<>(DEFAULT_DGPU_BUDGET_BYTES);
    }

    public int maxBytesPerFrame() {
        return maxBytesPerFrame;
    }

    public int pendingCount() {
        return pendingByKey.size();
    }

    public long pendingBytes() {
        return pendingBytes;
    }

    public long totalAdmittedCount() {
        return totalAdmittedCount;
    }

    public long totalAdmittedBytes() {
        return totalAdmittedBytes;
    }

    public long totalSupersededCount() {
        return totalSupersededCount;
    }

    public long totalDeferredFrames() {
        return totalDeferredFrames;
    }

    /**
     * Enqueues an upload payload. If an older pending entry exists for the same
     * {@code sectionPassKey}, the older payload is released immediately and superseded.
     */
    public void enqueue(long sectionPassKey, int byteSize, Priority priority, int generation, T payload) {
        Entry<T> entry = new Entry<>(sectionPassKey, byteSize, priority, generation, payload);
        Long boxedKey = sectionPassKey;
        Entry<T> previous = pendingByKey.put(boxedKey, entry);
        if (previous != null) {
            pendingBytes -= previous.byteSize();
            totalSupersededCount++;
            closeQuietly(previous.payload());
            if (previous.priority() == Priority.BACKGROUND && priority == Priority.IMMEDIATE) {
                immediateOrder.addLast(boxedKey);
            }
        } else {
            if (priority == Priority.IMMEDIATE) {
                immediateOrder.addLast(boxedKey);
            } else {
                backgroundOrder.addLast(boxedKey);
            }
        }
        pendingBytes += byteSize;
    }

    /**
     * Cancels and releases any pending upload for {@code sectionPassKey} (e.g. on section unload).
     */
    public boolean cancel(long sectionPassKey) {
        Entry<T> removed = pendingByKey.remove(sectionPassKey);
        if (removed == null) {
            return false;
        }
        pendingBytes -= removed.byteSize();
        closeQuietly(removed.payload());
        return true;
    }

    /**
     * Drains up to {@link #maxBytesPerFrame()} bytes into {@code out} (or {@code consumer}),
     * prioritizing {@link Priority#IMMEDIATE} before {@link Priority#BACKGROUND}.
     * Caller assumes ownership of the returned payloads and is responsible for closing them
     * after recording the GPU copy.
     */
    public DrainStats drainFrame(List<Entry<T>> out) {
        Objects.requireNonNull(out, "out");
        return drainFrame((Consumer<Entry<T>>) out::add);
    }

    public DrainStats drainFrame(Consumer<Entry<T>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        int admitted = 0;
        long bytesUsed = 0L;

        while (true) {
            Entry<T> next = peekNextLive(immediateOrder, Priority.IMMEDIATE);
            boolean fromImmediate = true;
            if (next == null) {
                next = peekNextLive(backgroundOrder, Priority.BACKGROUND);
                fromImmediate = false;
            }
            if (next == null) {
                break;
            }
            if (admitted > 0 && bytesUsed + next.byteSize() > maxBytesPerFrame) {
                totalDeferredFrames++;
                break;
            }
            if (fromImmediate) {
                immediateOrder.pollFirst();
            } else {
                backgroundOrder.pollFirst();
            }
            pendingByKey.remove(next.sectionPassKey());
            pendingBytes -= next.byteSize();
            bytesUsed += next.byteSize();
            admitted++;
            totalAdmittedCount++;
            totalAdmittedBytes += next.byteSize();
            consumer.accept(next);
        }

        return new DrainStats(admitted, bytesUsed, pendingByKey.size(), pendingBytes);
    }

    private Entry<T> peekNextLive(ArrayDeque<Long> queue, Priority expectedPriority) {
        while (!queue.isEmpty()) {
            Long key = queue.peekFirst();
            Entry<T> live = pendingByKey.get(key);
            if (live == null || live.priority() != expectedPriority) {
                queue.pollFirst();
                continue;
            }
            return live;
        }
        return null;
    }

    /** Cancels and releases all pending uploads (e.g. on world unload). */
    public void clear() {
        for (Entry<T> entry : pendingByKey.values()) {
            closeQuietly(entry.payload());
        }
        pendingByKey.clear();
        immediateOrder.clear();
        backgroundOrder.clear();
        pendingBytes = 0L;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Release hook must not abort queue maintenance.
        }
    }
}
