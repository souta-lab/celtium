package org.taumc.celeritas.api.eventbus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the delivery contract documented on {@link EmbeddiumEvent} (L08-07): an event reaches the
 * handlers registered on its own registrar, and nothing else. Posting never dispatches to a
 * platform (Forge) event bus, so the platform hook never contributes a cancellation.
 */
class EventHandlerRegistrarTest {

    private static class TestEvent extends EmbeddiumEvent {
        private final boolean cancelable;

        TestEvent(boolean cancelable) {
            this.cancelable = cancelable;
        }

        @Override
        public boolean isCancelable() {
            return cancelable;
        }
    }

    @Test
    void postDeliversTheEventToEveryRegisteredListener() {
        EventHandlerRegistrar<TestEvent> registrar = new EventHandlerRegistrar<>();
        List<TestEvent> received = new ArrayList<>();
        registrar.addListener(received::add);
        registrar.addListener(received::add);

        TestEvent event = new TestEvent(false);
        boolean canceled = registrar.post(event);

        assertFalse(canceled);
        assertEquals(List.of(event, event), received);
    }

    @Test
    void postReturnsTrueWhenAListenerCancelsACancelableEvent() {
        EventHandlerRegistrar<TestEvent> registrar = new EventHandlerRegistrar<>();
        registrar.addListener(event -> event.setCanceled(true));

        assertTrue(registrar.post(new TestEvent(true)));
    }

    @Test
    void postIgnoresCancellationOfANonCancelableEvent() {
        EventHandlerRegistrar<TestEvent> registrar = new EventHandlerRegistrar<>();
        registrar.addListener(event -> event.setCanceled(true));

        assertFalse(registrar.post(new TestEvent(false)));
    }

    @Test
    void postWithNoListenersReturnsFalse() {
        EventHandlerRegistrar<TestEvent> registrar = new EventHandlerRegistrar<>();

        assertFalse(registrar.post(new TestEvent(true)));
    }
}
