package org.soutalab.celtium.vulkan;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one adjudication point for connections whose feature overlaps Celtium's own - the
 * application-gate shape (the Naughthirium analysis's B1, reimplemented as a pattern): for each
 * connection, "is the partner present" x "does the partner's own setting enable it" x "who
 * wins" is combined here and nowhere else, and the answer is a state plus the reason that
 * produced it. Callers supply the terms and act on the answer; they never re-derive the terms
 * locally, and a connection that loses stands down completely, so the same feature is never
 * managed twice.
 * <p>
 * Adjudication is deliberately not on any frame path: the terms are gathered once by the
 * supplying side (mod presence and the partner's enablement do not change while a renderer
 * lives), the decision is cached there, and this class only combines them. Every decision is
 * also reported through {@link #reportConnection} when it is first made and whenever it
 * changes, so a connection that never fires - the failure mode this gate exists to remove -
 * names itself and its reason in the log instead of staying silent.
 */
public final class ConnectionGate {

    /** Whose dynamic lights run, as adjudicated for the item-378 connection. */
    public enum DynamicLightsState {
        /** Celeritas Dynamic Lights is present and enabled by its own mode: it wins. */
        EXTERNAL,
        /** No enabled external: Celtium's built-in service serves, per its property term. */
        NATIVE,
        /** Nobody serves dynamic lights. */
        OFF
    }

    /** An adjudicated dynamic-lights decision: the state plus the reason that produced it. */
    public static final class DynamicLightsDecision {
        private final DynamicLightsState state;
        private final String reason;

        private DynamicLightsDecision(DynamicLightsState state, String reason) {
            this.state = state;
            this.reason = reason;
        }

        public DynamicLightsState state() {
            return this.state;
        }

        public String reason() {
            return this.reason;
        }
    }

    private ConnectionGate() {
    }

    /**
     * Adjudicates the dynamic-lights connection from its three terms. An enabled external wins
     * outright: the native gather and its CPU mirror stand down, because the external raises
     * the same sources through the combined-light surface and running both would double them.
     * An external that is present but disabled by its own setting leaves the native property
     * in charge, with the reason saying so; absence adjudicates the same, minus the note.
     */
    public static DynamicLightsDecision dynamicLights(boolean externalPresent, boolean externalEnabled,
                                                      boolean nativeProperty) {
        if (externalPresent && externalEnabled) {
            return new DynamicLightsDecision(DynamicLightsState.EXTERNAL,
                    "celeritasdynamiclights present and enabled by its own mode; native service stands down");
        }
        if (externalPresent) {
            return nativeProperty
                    ? new DynamicLightsDecision(DynamicLightsState.NATIVE,
                            "celeritasdynamiclights present but disabled by its own mode; native property on")
                    : new DynamicLightsDecision(DynamicLightsState.OFF,
                            "celeritasdynamiclights present but disabled by its own mode; native property off");
        }
        return nativeProperty
                ? new DynamicLightsDecision(DynamicLightsState.NATIVE,
                        "no external dynamic lights; native property on")
                : new DynamicLightsDecision(DynamicLightsState.OFF,
                        "no external dynamic lights; native property off");
    }

    private static final Map<String, String> LAST_REPORTED = new ConcurrentHashMap<>();

    /**
     * Prints the one-line state of a connection - {@code CELTIUM_CONNECTION target=... state=...
     * reason=...} - the first time it is reported and whenever the reported state or reason
     * changes. One line per change, never per frame: the {@code [Celtium]} tag is rate-limited,
     * and a connection's state is a fact about the session, not a measurement.
     */
    public static void reportConnection(String target, String state, String reason) {
        String signature = state + '|' + reason;
        if (signature.equals(LAST_REPORTED.put(target, signature))) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_CONNECTION target=" + target
                + " state=" + state + " reason=" + reason);
    }
}
