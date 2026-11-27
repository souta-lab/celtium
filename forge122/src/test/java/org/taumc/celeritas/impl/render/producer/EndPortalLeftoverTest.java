package org.taumc.celeritas.impl.render.producer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DP-30: a collection whose window never flushes (an exception between collect and the end of the
 * block-entity pass) is counted at the next window's {@code begin()} instead of vanishing - the
 * portal render is cancelled at collect time, so an uncounted leftover is geometry that left the
 * picture with no record. The leftover is also dropped: drawing a dead frame's collection under the
 * next frame's state would be a second defect traded for the first.
 */
class EndPortalLeftoverTest {

    @Test
    void anUnflushedCollectionIsCountedAndDroppedAtTheNextBegin() {
        EndPortalProducer.begin();
        EndPortalProducer.collect(false, 1.0, 2.0, 3.0, EndPortalProducer.UP, 1, 0.0f);
        EndPortalProducer.collect(true, 4.0, 5.0, 6.0, EndPortalProducer.SOUTH, 2, 0.0f);
        // No flush: the window died mid-pass. The next begin must see both blocks as leftovers.
        EndPortalProducer.begin();
        String description = EndPortalProducer.describe();
        assertTrue(description.contains("leftoverWindows=1"), description);
        assertTrue(description.contains("leftoverBlocks=2"), description);
        // And they are gone: a flush now has nothing to draw and a further begin counts nothing new.
        EndPortalProducer.flush();
        EndPortalProducer.begin();
        assertTrue(EndPortalProducer.describe().contains("leftoverWindows=1"),
                EndPortalProducer.describe());
    }

    @Test
    void aFlushedCollectionLeavesNothingBehind() {
        EndPortalProducer.begin();
        // An empty window flushes clean (flush with no blocks returns before any draw).
        EndPortalProducer.flush();
        long windows = leftoverWindows();
        EndPortalProducer.begin();
        assertTrue(leftoverWindows() == windows, "a clean window adds no leftover");
    }

    private static long leftoverWindows() {
        String description = EndPortalProducer.describe();
        int at = description.indexOf("leftoverWindows=");
        long value = 0;
        for (int i = at + "leftoverWindows=".length();
                i < description.length() && Character.isDigit(description.charAt(i)); i++) {
            value = value * 10 + (description.charAt(i) - '0');
        }
        return value;
    }
}
