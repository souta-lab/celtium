package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.Iterator;
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
 * <p>
 * Every entry names the device it belongs to (the device handle, audit L06-01 / BH10-09). The tag is what keeps one
 * device's teardown from becoming another device's destruction: an entry left behind by a backend teardown used to sit
 * in this static queue with no owner, and the next backend's frame-path drain then ran it - vkDestroyImageView and
 * friends against a device that no longer existed, or, where the driver had reused the handle value, against the new
 * device's live resources. Drains are therefore scoped by device ({@link #flushFor(long)}): a backend drains its own
 * device's entries, {@code VulkanContext.delete()} drains its device's entries as its last act before destroying the
 * device, and entries whose device is gone without a confirming wait are dropped and counted ({@link #dropFor(long)})
 * rather than executed. The unscoped {@link #flush()} remains for callers that have established device-wide idleness
 * for every device at once; production drains are all scoped.
 */
public final class VulkanRetireQueue {

    /** One pending destruction and the handle of the device it belongs to; 0 when the owner is unknown. */
    private static final class Entry {
        final long device;
        final Runnable destroyer;

        Entry(long device, Runnable destroyer) {
            this.device = device;
            this.destroyer = destroyer;
        }
    }

    private static final List<Entry> PENDING = new ArrayList<Entry>();
    private static volatile boolean hasPending;
    private static long retired;
    private static long flushed;
    private static long dropped;

    private VulkanRetireQueue() {
    }

    /** Hands a destruction over instead of performing it, without naming its device. Prefer {@link #retire(long, Runnable)}. */
    public static void retire(Runnable destroyer) {
        retire(0L, destroyer);
    }

    /** Hands a destruction over instead of performing it, owned by {@code device} (a VkDevice handle). */
    public static synchronized void retire(long device, Runnable destroyer) {
        PENDING.add(new Entry(device, destroyer));
        retired++;
        hasPending = true;
    }

    /** Performs every pending destruction. Only safe once a wait for device idle has actually succeeded. */
    public static int flush() {
        if (!hasPending) {
            return 0;
        }
        return flushSynchronized();
    }

    private static synchronized int flushSynchronized() {
        if (PENDING.isEmpty()) {
            hasPending = false;
            return 0;
        }
        List<Runnable> batch = new ArrayList<Runnable>(PENDING.size());
        for (Entry entry : PENDING) {
            batch.add(entry.destroyer);
        }
        PENDING.clear();
        hasPending = false;
        runBatch(batch);
        return batch.size();
    }

    /**
     * Performs every pending destruction owned by {@code device}, and only those. Only safe once a wait for that
     * device's idle has actually succeeded. Entries another device owns are left queued for their own drain.
     */
    public static int flushFor(long device) {
        if (!hasPending) {
            return 0;
        }
        return flushForSynchronized(device);
    }

    private static synchronized int flushForSynchronized(long device) {
        List<Runnable> batch = drainMatching(device);
        if (batch.isEmpty()) {
            return 0;
        }
        runBatch(batch);
        return batch.size();
    }

    /**
     * Removes {@code device}'s pending destructions without performing them, and returns how many there were.
     * <p>
     * For a device that is being destroyed without a wait that confirmed it idle (device loss, or a failed wait on a
     * teardown that destroys the device anyway): the destructions can never legally run afterwards, so they are
     * counted and left to the process teardown, the same disposition {@code VulkanContext.delete()} gives its
     * quarantined resources. Dropping them is what stops a later backend's drain from executing them against a dead
     * device handle.
     */
    public static synchronized int dropFor(long device) {
        if (PENDING.isEmpty()) {
            return 0;
        }
        int count = 0;
        Iterator<Entry> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().device == device) {
                iterator.remove();
                count++;
            }
        }
        if (PENDING.isEmpty()) {
            hasPending = false;
        }
        dropped += count;
        return count;
    }

    /** Takes {@code device}'s entries out of the queue; entries handed over while the batch runs stay queued. */
    private static List<Runnable> drainMatching(long device) {
        List<Runnable> batch = new ArrayList<Runnable>();
        Iterator<Entry> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.device == device) {
                batch.add(entry.destroyer);
                iterator.remove();
            }
        }
        if (PENDING.isEmpty()) {
            hasPending = false;
        }
        return batch;
    }

    private static void runBatch(List<Runnable> batch) {
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
    }

    public static synchronized int pending() {
        return PENDING.size();
    }

    /** {@return how many destructions owned by {@code device} are pending right now}. */
    public static synchronized int pendingFor(long device) {
        int count = 0;
        for (Entry entry : PENDING) {
            if (entry.device == device) {
                count++;
            }
        }
        return count;
    }

    public static synchronized String describe() {
        return "retired=" + retired + " flushed=" + flushed + " dropped=" + dropped + " pending=" + PENDING.size();
    }
}
