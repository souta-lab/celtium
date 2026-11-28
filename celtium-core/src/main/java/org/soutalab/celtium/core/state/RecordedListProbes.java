package org.soutalab.celtium.core.state;

/**
 * The installed {@link RecordedListProbe} (E5 seat C, Neon E4 ruling 2026-11-28).
 * <p>
 * The composition root - today {@code CeleritasLoadingPlugin}, which installs the capture layer
 * itself - installs the query over the capture layer's recorded-list store at startup. Until
 * then {@link #current()} answers {@link RecordedListProbe#NONE}, which is also the correct
 * answer in unit tests and in any embedding without a capture layer: no list is recorded and
 * no list is executing.
 * <p>
 * The backing store is the capture layer's own and is expected to move with it (the GL-less
 * stub's ledger today; the Lane 3 capture entrance's store after the E4-b entrance switch).
 * The contract in {@link RecordedListProbe} does not change when the store moves; only the
 * installation site re-points.
 */
public final class RecordedListProbes {

    private static volatile RecordedListProbe current = RecordedListProbe.NONE;

    private RecordedListProbes() {
    }

    /** {@return the installed probe, or {@link RecordedListProbe#NONE} when none is installed}. */
    public static RecordedListProbe current() {
        return current;
    }

    /** Installs the composition root's probe; {@code null} restores the uninstalled answer. */
    public static void install(RecordedListProbe probe) {
        current = probe == null ? RecordedListProbe.NONE : probe;
    }
}
