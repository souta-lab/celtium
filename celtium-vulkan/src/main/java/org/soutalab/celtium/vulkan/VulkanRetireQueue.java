package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.List;

/**
 * Destruction that waits for the GPU (audit S12).
 * <p>
 * {@code invalidate} and {@code resize} used to destroy images, views and memory immediately, while a command buffer
 * recorded earlier could still name them - the audit reproduced exactly that on the reload and world-leave paths. The
 * resources are handed here instead, and the backend drains this queue only after a wait that actually succeeded, so
 * nothing is freed while the device may still be reading it.
 * <p>
 * The queue is static because the objects being destroyed do not hold a backend reference; it is drained by the backend,
 * which is the only party that knows when the device is idle.
 */
public final class VulkanRetireQueue {

    private static final List<Runnable> PENDING = new ArrayList<Runnable>();
    private static long retired;
    private static long flushed;

    private VulkanRetireQueue() {
    }

    /** Hands a destruction over instead of performing it. */
    public static synchronized void retire(Runnable destroyer) {
        PENDING.add(destroyer);
        retired++;
    }

    /** Performs every pending destruction. Only safe once a wait for device idle has actually succeeded. */
    public static synchronized int flush() {
        if (PENDING.isEmpty()) {
            return 0;
        }
        List<Runnable> batch = new ArrayList<Runnable>(PENDING);
        PENDING.clear();
        for (Runnable destroyer : batch) {
            try {
                destroyer.run();
            } catch (RuntimeException | LinkageError error) {
                // A resource that cannot be destroyed is leaked rather than fatal: the alternative is taking the
                // renderer down over a cleanup failure, which the audit explicitly warns against.
                System.err.println("[Celtium] retired resource could not be destroyed: " + error);
            }
        }
        flushed += batch.size();
        return batch.size();
    }

    public static synchronized int pending() {
        return PENDING.size();
    }

    public static synchronized String describe() {
        return "retired=" + retired + " flushed=" + flushed + " pending=" + PENDING.size();
    }
}
