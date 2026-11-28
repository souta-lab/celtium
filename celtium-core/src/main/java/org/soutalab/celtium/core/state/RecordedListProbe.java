package org.soutalab.celtium.core.state;

/**
 * The read-only recorded-display-list boundary (E5 seat C, Neon E4 ruling 2026-11-28).
 * <p>
 * The model path needs two answers about display lists the capture layer recorded: whether a
 * list with no captured model quads still holds recorded commands a replay can draw, and which
 * list a replay is currently executing. Both answers live in the capture layer's own store, and
 * the non-terrain lane must not reach into that store directly: G2-07 keeps the recorded-list
 * boundary explicitly separated, and the G2-14 wiring contract forbids Lane 2 classes from
 * referencing the converter ({@code org.soutalab.celtium.glcompat}) or the retired monolith
 * GL package at all. This narrow predicate is the whole surface:
 * the composition root installs the query over the real store (see {@link RecordedListProbes}),
 * and readers here stay free of both packages.
 * <p>
 * The compiling side of the same question is deliberately not here: the list being compiled is
 * fixed-function state and is read from {@link LightStateRegister#compilingListId()} (G2-07
 * ruling, plan section 8.3 item 16). This probe covers only the recorded/executing side, which
 * is the capture store's own bookkeeping.
 */
public interface RecordedListProbe {

    /**
     * {@return whether the capture layer holds a recorded command list for this name}.
     * <p>
     * The model path asks this before it reports a list as missing: a list with no captured
     * model quads can still be replayable, because the capture layer records the commands of a
     * list compiled through {@code GlStateManager.glNewList} and a Tessellator draw inside it
     * becomes a recorded draw entry. A mod's skybox is exactly that shape, and reporting it as
     * a missing mesh counted geometry that the recorded replay draws.
     */
    boolean hasRecordedList(int listId);

    /**
     * {@return the display list a replay is currently executing, or 0 when none is}.
     * <p>
     * {@code GL_LIST_INDEX} answers a different question - it names the list being compiled and
     * the spec keeps it at 0 while a list is being executed - so the model capture, which files
     * a quad under the list it belongs to, needs the replay side answered from the store that
     * runs the replay.
     */
    int executingListId();

    /** The uninstalled answer: nothing is recorded and nothing is executing. */
    RecordedListProbe NONE = new RecordedListProbe() {
        @Override
        public boolean hasRecordedList(int listId) {
            return false;
        }

        @Override
        public int executingListId() {
            return 0;
        }
    };
}
