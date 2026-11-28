package org.soutalab.celtium.glcompat.capture;

/**
 * The dispatch thread gate extracted from {@code GlsmFrontend} at S3-b E4-a: a call that arrives while
 * another thread is mid-dispatch is refused, so the capture machinery never serves two threads at once.
 * One of the four survivor responsibilities of the capture entrance (Neon E4 ruling section 1.1).
 */
public final class DispatchThreadGate {
    /** The arriving thread now occupies dispatch (its depth went 0 to 1). */
    public static final int ENTERED = 0;
    /** The arriving thread already occupied dispatch and re-entered (its depth grew). */
    public static final int REENTERED = 1;
    /** Another thread occupies dispatch; the call must not proceed. */
    public static final int REFUSED = -1;

    private final java.util.concurrent.atomic.AtomicReference<Thread> active =
            new java.util.concurrent.atomic.AtomicReference<Thread>();
    /** Nested-dispatch depth of the occupying thread; only that thread touches it. */
    private int depth;

    public int enter(Thread self) {
        Thread current = this.active.get();
        if (current == self) {
            this.depth++;
            return REENTERED;
        }
        if (current == null && this.active.compareAndSet(null, self)) {
            this.depth = 1;
            return ENTERED;
        }
        return REFUSED;
    }

    public void exit(Thread self) {
        if (--this.depth == 0) {
            this.active.set(null);
        }
    }

    /** {@return the thread inside dispatch, or {@code null} when the gate is free}; for refusal lines. */
    public Thread activeThread() {
        return this.active.get();
    }
}
