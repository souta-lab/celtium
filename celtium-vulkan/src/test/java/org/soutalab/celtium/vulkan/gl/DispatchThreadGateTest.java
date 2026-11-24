package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GlsmFrontend.DispatchThreadGate} (audit-gl-deep G2, remainder): the state tables are confined to
 * the thread inside dispatch, so the gate must admit exactly one thread at a time, admit that same thread
 * again while it is inside (list replay re-enters), refuse a foreign thread for exactly the span the owner
 * is inside, and serve sequential arrivals from any thread once the gate is free. All ordering here is fixed
 * with latches, never with sleeps: a refusal is only ever asserted while the owner is provably inside.
 */
class DispatchThreadGateTest {

    @Test
    void firstArrivalEntersAndReleaseFreesTheGate() throws Exception {
        GlsmFrontend.DispatchThreadGate gate = new GlsmFrontend.DispatchThreadGate();
        Thread self = Thread.currentThread();
        assertEquals(GlsmFrontend.DispatchThreadGate.ENTERED, gate.enter(self));
        assertSame(self, gate.activeThread());
        gate.exit(self);
        assertNull(gate.activeThread());

        AtomicInteger otherResult = new AtomicInteger(Integer.MIN_VALUE);
        Thread other = new Thread(() -> {
            Thread me = Thread.currentThread();
            otherResult.set(gate.enter(me));
            gate.exit(me);
        });
        other.start();
        other.join(TimeUnit.SECONDS.toMillis(10));
        assertEquals(GlsmFrontend.DispatchThreadGate.ENTERED, otherResult.get());
        assertNull(gate.activeThread());
    }

    @Test
    void reentryIsTrackedByDepth() throws Exception {
        GlsmFrontend.DispatchThreadGate gate = new GlsmFrontend.DispatchThreadGate();
        Thread self = Thread.currentThread();
        assertEquals(GlsmFrontend.DispatchThreadGate.ENTERED, gate.enter(self));
        assertEquals(GlsmFrontend.DispatchThreadGate.REENTERED, gate.enter(self));
        gate.exit(self);
        // One exit only unwinds the nested entry: the gate is still held, so a foreign thread is refused.
        assertSame(self, gate.activeThread());
        AtomicInteger duringNested = new AtomicInteger(Integer.MIN_VALUE);
        Thread foreign = new Thread(() -> duringNested.set(gate.enter(Thread.currentThread())));
        foreign.start();
        foreign.join(TimeUnit.SECONDS.toMillis(10));
        assertEquals(GlsmFrontend.DispatchThreadGate.REFUSED, duringNested.get());
        gate.exit(self);
        assertNull(gate.activeThread());
    }

    @Test
    void foreignArrivalDuringDispatchIsRefusedThenServedAfterRelease() throws Exception {
        GlsmFrontend.DispatchThreadGate gate = new GlsmFrontend.DispatchThreadGate();
        CountDownLatch ownerInside = new CountDownLatch(1);
        CountDownLatch ownerMayLeave = new CountDownLatch(1);
        AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
        Thread owner = new Thread(() -> {
            Thread me = Thread.currentThread();
            try {
                assertEquals(GlsmFrontend.DispatchThreadGate.ENTERED, gate.enter(me));
                ownerInside.countDown();
                assertTrue(ownerMayLeave.await(10, TimeUnit.SECONDS));
                gate.exit(me);
            } catch (Throwable failure) {
                ownerFailure.set(failure);
            }
        });
        owner.start();
        assertTrue(ownerInside.await(10, TimeUnit.SECONDS));
        // The owner is provably inside: this thread's arrival overlaps its dispatch and is refused.
        assertEquals(GlsmFrontend.DispatchThreadGate.REFUSED, gate.enter(Thread.currentThread()));
        assertSame(owner, gate.activeThread());
        ownerMayLeave.countDown();
        owner.join(TimeUnit.SECONDS.toMillis(10));
        assertNull(ownerFailure.get());
        // After the release the same thread is a sequential arrival and is served.
        Thread self = Thread.currentThread();
        assertEquals(GlsmFrontend.DispatchThreadGate.ENTERED, gate.enter(self));
        gate.exit(self);
        assertNull(gate.activeThread());
    }

    @Test
    void sequentialArrivalsFromDifferentThreadsAreAllServed() throws Exception {
        GlsmFrontend.DispatchThreadGate gate = new GlsmFrontend.DispatchThreadGate();
        for (int round = 0; round < 3; round++) {
            AtomicInteger result = new AtomicInteger(Integer.MIN_VALUE);
            Thread arrival = new Thread(() -> {
                Thread me = Thread.currentThread();
                result.set(gate.enter(me));
                gate.exit(me);
            });
            arrival.start();
            arrival.join(TimeUnit.SECONDS.toMillis(10));
            assertEquals(GlsmFrontend.DispatchThreadGate.ENTERED, result.get());
            assertNull(gate.activeThread());
        }
        Thread self = Thread.currentThread();
        assertEquals(GlsmFrontend.DispatchThreadGate.ENTERED, gate.enter(self));
        gate.exit(self);
    }
}
