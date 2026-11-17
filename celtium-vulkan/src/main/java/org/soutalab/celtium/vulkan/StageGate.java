package org.soutalab.celtium.vulkan;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The recorded registry of the native stages that can fail independently - the kill switches and the runtime
 * self-quarantine, in one place. Inherited from Vulcanizator's survival layer (survey V-01/V-02): every native
 * stage there is independently disable-able, and a stage that fails at runtime marks itself quarantined and is
 * never retried, while the rest of the renderer keeps presenting through its fallback.
 * <p>
 * A stage has two independent off switches:
 * <ul>
 *   <li>the user's kill switch, {@code -Dceltium.vulkan.stage.<id>=off} (or {@code =false}), read once when the
 *   system gate is built. This is the switch support reaches for after a crash report names a stage on a hostile
 *   driver: the stage's commands are never even recorded, rather than recorded and rolled back.</li>
 *   <li>the runtime quarantine: {@link #trip} (immediate, for failures that would repeat identically - a pipeline
 *   or executor creation that failed once on this context) or {@link #noteFailure} reaching the stage's
 *   consecutive-failure threshold (for failures that can be transient). A quarantine is for the session and
 *   never auto-clears; a success resets only the consecutive count, never a quarantine.</li>
 * </ul>
 * The present stage is deliberately not a stage here: a fatal presenter result is terminal by the tree's recorded
 * evidence (see {@code VulkanContext.presenterResultIsFatal}), so its survival shape is the hardware-profile
 * gate before start ({@link HardwareProfileGates}), not a runtime fallback. The async presenter's own switch
 * stays {@code -Dceltium.vulkan.asyncPresent}.
 */
public final class StageGate {

    /** A native stage that can fail - and therefore be switched off or quarantined - independently. */
    public enum Stage {
        /** Terrain pipeline creation ({@code TerrainRenderer.pipelineFor}); degradation is a skipped pass. */
        TERRAIN_PIPELINES("terrainPipelines", Integer.MAX_VALUE),
        /** The native post chain ({@code PostChainBridge}); degradation is the caller's GL chain. */
        POST_CHAIN("postChain", 3);

        private final String id;
        private final int failureThreshold;

        Stage(String id, int failureThreshold) {
            this.id = id;
            this.failureThreshold = failureThreshold;
        }

        /** {@return the stage's id, as it appears in its kill-switch property and in diagnostics}. */
        public String id() {
            return this.id;
        }

        /** {@return the JVM property that switches this stage off}. */
        public String switchProperty() {
            return "celtium.vulkan.stage." + this.id;
        }

        /** {@return consecutive failures that quarantine this stage; {@code Integer.MAX_VALUE} = never}. */
        int failureThreshold() {
            return this.failureThreshold;
        }
    }

    /** Per-stage mutable state; every access is synchronized on the gate. */
    private static final class State {
        private boolean switchedOff;
        private boolean quarantined;
        private int consecutiveFailures;
        private long failures;
        private int trips;
    }

    private final EnumMap<Stage, State> states = new EnumMap<>(Stage.class);
    private Consumer<String> diagnostics = StageGate::printDiagnostic;

    private StageGate(Set<Stage> switchedOff) {
        for (Stage stage : Stage.values()) {
            State state = new State();
            state.switchedOff = switchedOff.contains(stage);
            this.states.put(stage, state);
        }
    }

    private static void printDiagnostic(String line) {
        System.out.println("[Celtium] " + line);
    }

    private static volatile StageGate system;

    /** {@return the shared gate, built once from the kill-switch properties}. */
    public static StageGate system() {
        StageGate gate = system;
        if (gate == null) {
            synchronized (StageGate.class) {
                gate = system;
                if (gate == null) {
                    java.util.EnumSet<Stage> off = java.util.EnumSet.noneOf(Stage.class);
                    for (Stage stage : Stage.values()) {
                        if (isOffValue(System.getProperty(stage.switchProperty(), "on"))) {
                            off.add(stage);
                        }
                    }
                    gate = new StageGate(off);
                    system = gate;
                }
            }
        }
        return gate;
    }

    /** A gate with the given stages switched off; for tests. */
    static StageGate withSwitchedOff(Set<Stage> switchedOff) {
        return new StageGate(switchedOff);
    }

    /** Replaces the diagnostics sink; for tests. The default prints to stdout. */
    synchronized void setDiagnostics(Consumer<String> diagnostics) {
        this.diagnostics = diagnostics;
    }

    /** {@return whether the stage may run: not switched off by the user and not quarantined}. */
    public synchronized boolean enabled(Stage stage) {
        State state = this.states.get(stage);
        return !state.switchedOff && !state.quarantined;
    }

    /** {@return whether the user switched the stage off}. */
    public synchronized boolean switchedOff(Stage stage) {
        return this.states.get(stage).switchedOff;
    }

    /** {@return whether the stage has quarantined itself at runtime}. */
    public synchronized boolean quarantined(Stage stage) {
        return this.states.get(stage).quarantined;
    }

    /** {@return how many times the stage has quarantined itself (0 or 1 in practice; a trip is one-shot)}. */
    public synchronized int trips(Stage stage) {
        return this.states.get(stage).trips;
    }

    /** {@return the total failures noted for the stage, tripping or not}. */
    public synchronized long failures(Stage stage) {
        return this.states.get(stage).failures;
    }

    /** {@return the current run of consecutive failures, reset by {@link #noteSuccess}}. */
    public synchronized int consecutiveFailures(Stage stage) {
        return this.states.get(stage).consecutiveFailures;
    }

    /**
     * Notes one failure of the stage. When the consecutive count reaches the stage's threshold the stage
     * quarantines itself; a stage whose threshold is {@code Integer.MAX_VALUE} (terrain pipelines - their
     * per-pass remembered failure is the finer instrument) is only counted.
     */
    public void noteFailure(Stage stage, Throwable error) {
        boolean tripped;
        synchronized (this) {
            State state = this.states.get(stage);
            state.failures++;
            state.consecutiveFailures++;
            tripped = !state.quarantined && state.consecutiveFailures >= stage.failureThreshold();
            if (tripped) {
                state.quarantined = true;
                state.trips++;
            }
        }
        if (tripped) {
            this.diagnostics.accept(quarantineLine(stage, error));
        }
    }

    /**
     * Quarantines the stage immediately, for a failure that would repeat identically on every retry - the
     * corpse is never retried. One-shot: tripping an already-quarantined stage records nothing new.
     */
    public void trip(Stage stage, Throwable error) {
        boolean tripped;
        synchronized (this) {
            State state = this.states.get(stage);
            state.failures++;
            tripped = !state.quarantined;
            if (tripped) {
                state.quarantined = true;
                state.trips++;
            }
        }
        if (tripped) {
            this.diagnostics.accept(quarantineLine(stage, error));
        }
    }

    /** Notes one success of the stage: the consecutive-failure count resets; a quarantine stands. */
    public synchronized void noteSuccess(Stage stage) {
        this.states.get(stage).consecutiveFailures = 0;
    }

    private static String quarantineLine(Stage stage, Throwable error) {
        return "CELTIUM_STAGE_QUARANTINED stage=" + stage.id()
                + " error=" + (error == null ? "none" : error.getClass().getSimpleName())
                + " - the stage is off for this session; its fallback carries the frame";
    }

    /** {@return a one-line summary of every stage's switch, quarantine and counters, for diagnostics}. */
    public synchronized String summary() {
        StringBuilder out = new StringBuilder("stageGate[");
        boolean first = true;
        for (Stage stage : Stage.values()) {
            State state = this.states.get(stage);
            if (!first) {
                out.append(' ');
            }
            first = false;
            out.append(stage.id()).append('=');
            if (state.switchedOff) {
                out.append("off(kill-switch)");
            } else if (state.quarantined) {
                out.append("off(quarantined)");
            } else {
                out.append("on");
            }
            out.append(" failures=").append(state.failures);
            out.append(" trips=").append(state.trips);
        }
        return out.append(']').toString();
    }

    /** {@return the stage named by {@code id}, or null} - for diagnostics that read a stage from text. */
    public static Stage stageNamed(String id) {
        for (Stage stage : Stage.values()) {
            if (stage.id().equals(id)) {
                return stage;
            }
        }
        return null;
    }

    /** Kept for the switch parser's locale-stable comparisons. */
    static boolean isOffValue(String value) {
        return value != null && ("off".equals(value.toLowerCase(Locale.ROOT))
                || "false".equals(value.toLowerCase(Locale.ROOT)));
    }
}
