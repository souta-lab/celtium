package org.soutalab.celtium.vulkan.hal;

/**
 * How far behind the game the presented frame was, as {max, at most one, more than one}.
 * <p>
 * The backend counts the lag on its side of the present path and hands the totals to the frame
 * target for its timing summary; the record itself is pure state with no device in it. This is
 * the present-linkage half of the frame target's last functional group (HAL wave 3, group 8):
 * the semaphore side of that group is already an empty contract, so what remained in the frame
 * target was this record and the summary line that renders it.
 */
public final class PresentLagRecord {

    private long max;
    private long one;
    private long more;

    /** Sets the present-lag counters the summary reports. */
    public void set(long max, long one, long more) {
        this.max = max;
        this.one = one;
        this.more = more;
    }

    /** {@return the largest lag seen}, in frames. */
    public long max() {
        return this.max;
    }

    /** {@return how many presented frames lagged by at most one frame}. */
    public long one() {
        return this.one;
    }

    /** {@return how many presented frames lagged by more than one frame}. */
    public long more() {
        return this.more;
    }

    /** {@return the summary fragment}, in the frame target timing summary's own format. */
    public String describe() {
        return "presentLag=[max=" + this.max + ",one=" + this.one + ",more=" + this.more + "]";
    }
}
