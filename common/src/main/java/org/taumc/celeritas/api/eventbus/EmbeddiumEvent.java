package org.taumc.celeritas.api.eventbus;

/**
 * The base class which all Embeddium-posted events are derived from.
 * <p></p>
 * Events are delivered only through the {@link EventHandlerRegistrar} held by each event class
 * (for example, {@code OptionGUIConstructionEvent.BUS}); subscribe with
 * {@link EventHandlerRegistrar#addListener(EventHandlerRegistrar.Handler)}. Celtium does not fire
 * these events on the Forge event bus, and this class extends nothing on any platform Celtium
 * ships.
 * <p></p>
 * Upstream Embeddium/Celeritas preprocesses this class per loader, so on (Neo)Forge it extends
 * the native event class and the registrar also posts to the loader's event bus. Celtium's port
 * dropped the preprocessing and kept only the loader-less shape, so that second delivery channel
 * does not exist here; the platform dispatch hook in {@link EventHandlerRegistrar} is retained
 * but never dispatches.
 */
public abstract class EmbeddiumEvent {
    public boolean isCancelable() {
        return false;
    }

    private boolean canceled;

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean cancel) {
        canceled = cancel;
    }
}
