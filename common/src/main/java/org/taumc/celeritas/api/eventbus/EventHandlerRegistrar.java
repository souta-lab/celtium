package org.taumc.celeritas.api.eventbus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds a list of event handlers and handles event dispatching.
 */
public class EventHandlerRegistrar<T extends EmbeddiumEvent> {
    private final List<Handler<T>> handlerList = new CopyOnWriteArrayList<>();

    public EventHandlerRegistrar() {}

    public void addListener(Handler<T> listener) {
        handlerList.add(listener);
    }

    /**
     * Post the given event to all registered listeners.
     * @param event The event to post
     * @return true if the event is cancelable and was canceled, false otherwise
     */
    public boolean post(T event) {
        boolean canceled = false;

        // Skip doing work if the handler list is empty
        if(!handlerList.isEmpty()) {
            boolean isCancelable = event.isCancelable();
            for(Handler<T> handler : handlerList) {
                handler.acceptEvent(event);
                if(isCancelable && event.isCanceled()) {
                    canceled = true;
                }
            }
        }

        // Upstream also dispatches to the platform event bus here; Celtium never does (see below),
        // so this call always contributes false.
        canceled |= postPlatformSpecificEvent(event);
        return canceled;
    }

    /**
     * Retained hook from upstream Embeddium/Celeritas, whose per-loader preprocessing posts the
     * event to the (Neo)Forge event bus in this method. Celtium ships no preprocessing and its
     * events do not extend the Forge event class, so nothing is dispatched anywhere: delivery
     * happens only through the handlers registered on this registrar. Always returns false.
     */
    private static <T extends EmbeddiumEvent> boolean postPlatformSpecificEvent(T event) {
        return false;
    }

    @FunctionalInterface
    public interface Handler<T extends EmbeddiumEvent> {
        void acceptEvent(T event);
    }
}
