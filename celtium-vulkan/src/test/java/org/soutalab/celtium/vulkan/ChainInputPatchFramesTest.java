package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame schedule of the chain-input patch line (item 384 section 7): which frames the line samples,
 * and when a named frame becomes due.
 * <p>
 * The one-shot line reads the pair at the chain's first input, which precedes the first routed terrain
 * draw into the pair, so it can only ever read the pair before any mask reached it. The schedule exists to
 * sample at named frames at and after the routed pass start. Its contract has two halves: the spec text
 * names a sorted, de-duplicated, bounded set of frames (an unset or invalid-only spec names nothing and
 * the one-shot behaviour stands), and a named frame is due from its own frame on - never before it - until
 * the report claims it, one claim per window.
 * <p>
 * What these tests prove: the parse table of {@code celtium$parseChainInputPatchFrames} and the due table
 * of {@code celtium$chainInputPatchDue}, walked the way {@code celtium$reportChainInputPatch} walks them.
 * What the sampled value is at a given frame is the discrimination leg's measurement, not this test's.
 */
class ChainInputPatchFramesTest {

    @Test
    void unsetBlankAndInvalidOnlySpecsSelectTheOneShot() {
        assertNull(ModPrograms.celtium$parseChainInputPatchFrames(null),
                "an unset property keeps the one-shot behaviour");
        assertNull(ModPrograms.celtium$parseChainInputPatchFrames(""));
        assertNull(ModPrograms.celtium$parseChainInputPatchFrames("   "));
        assertNull(ModPrograms.celtium$parseChainInputPatchFrames("abc"),
                "a value naming no valid frame is the one-shot behaviour, not an empty schedule");
        assertNull(ModPrograms.celtium$parseChainInputPatchFrames("-1"),
                "a negative frame names nothing");
    }

    @Test
    void aSingleFrameParses() {
        assertArrayEquals(new long[]{55L}, ModPrograms.celtium$parseChainInputPatchFrames("55"));
        assertArrayEquals(new long[]{0L}, ModPrograms.celtium$parseChainInputPatchFrames("0"),
                "frame zero is a frame like any other");
    }

    @Test
    void aListIsSortedAndDeduplicated() {
        assertArrayEquals(new long[]{55L, 62L, 100L},
                ModPrograms.celtium$parseChainInputPatchFrames("100,55,62,55"),
                "the schedule is walked in frame order, and a frame named twice is sampled once");
    }

    @Test
    void rangesExpandInclusively() {
        assertArrayEquals(new long[]{60L, 61L, 62L, 63L},
                ModPrograms.celtium$parseChainInputPatchFrames("60-63"),
                "both ends of a range are sampled");
        assertArrayEquals(new long[]{55L, 60L, 61L},
                ModPrograms.celtium$parseChainInputPatchFrames("55,60-61"));
    }

    @Test
    void invalidItemsNameNothingAndAreSkipped() {
        assertArrayEquals(new long[]{55L}, ModPrograms.celtium$parseChainInputPatchFrames("abc,55"),
                "a non-number is skipped, the rest of the schedule stands");
        assertArrayEquals(new long[]{55L}, ModPrograms.celtium$parseChainInputPatchFrames("70-60,55"),
                "a reversed range names nothing");
        assertArrayEquals(new long[]{55L}, ModPrograms.celtium$parseChainInputPatchFrames("5-,55"),
                "a range with no end names nothing");
        assertArrayEquals(new long[]{55L}, ModPrograms.celtium$parseChainInputPatchFrames("-5,55"),
                "a negative frame names nothing");
        assertArrayEquals(new long[]{55L}, ModPrograms.celtium$parseChainInputPatchFrames(",55,"),
                "blank items name nothing");
    }

    @Test
    void whitespaceIsTolerated() {
        assertArrayEquals(new long[]{55L, 62L}, ModPrograms.celtium$parseChainInputPatchFrames(" 55 , 62 "));
    }

    @Test
    void theScheduleIsCapped() {
        long[] parsed = ModPrograms.celtium$parseChainInputPatchFrames("0-5000");
        assertEquals(ModPrograms.CHAIN_INPUT_PATCH_FRAME_CAP, parsed.length,
                "a range wider than the cap is truncated at the cap, not honoured without bound");
        assertEquals(0L, parsed[0]);
        assertEquals(ModPrograms.CHAIN_INPUT_PATCH_FRAME_CAP - 1L, parsed[parsed.length - 1],
                "the truncation keeps the earliest frames");
    }

    @Test
    void aNamedFrameIsNotDueBeforeItsFrame() {
        long[] schedule = {55L, 62L};
        assertFalse(ModPrograms.celtium$chainInputPatchDue(schedule, 0, 54L),
                "one frame before the routed pass start the sample is not taken yet");
        assertFalse(ModPrograms.celtium$chainInputPatchDue(schedule, 1, 61L),
                "each named frame has its own boundary");
    }

    @Test
    void aNamedFrameIsDueAtAndAfterItsFrameUntilClaimed() {
        long[] schedule = {55L, 62L};
        assertTrue(ModPrograms.celtium$chainInputPatchDue(schedule, 0, 55L),
                "at the named frame itself the sample is due - the routed pass start is the first frame "
                        + "whose read can separate the gates");
        assertTrue(ModPrograms.celtium$chainInputPatchDue(schedule, 0, 56L),
                "an unclaimed frame stays due: the claim happens at the first chain-input window at or "
                        + "after it, which may be a later frame");
        assertTrue(ModPrograms.celtium$chainInputPatchDue(schedule, 1, 62L));
    }

    @Test
    void anExhaustedOrAbsentScheduleIsNeverDue() {
        long[] schedule = {55L};
        assertFalse(ModPrograms.celtium$chainInputPatchDue(schedule, 1, 1000L),
                "past the last named frame nothing more is sampled");
        assertFalse(ModPrograms.celtium$chainInputPatchDue(null, 0, 1000L),
                "no schedule is the one-shot behaviour; this predicate is not consulted for it");
    }

    @Test
    void theClaimWalkSpendsEachNamedFrameOnceInOrder() {
        long[] schedule = {55L, 62L};
        int next = 0;
        long[] walk = {54L, 55L, 55L, 61L, 62L, 1000L};
        int[] claimsAt = new int[walk.length];
        for (int i = 0; i < walk.length; i++) {
            if (ModPrograms.celtium$chainInputPatchDue(schedule, next, walk[i])) {
                claimsAt[i] = 1;
                next++;
            }
        }
        assertArrayEquals(new int[]{0, 1, 0, 0, 1, 0}, claimsAt,
                "each named frame claims exactly one window: the first at or after it");
        assertEquals(2, next, "the walk ends with the schedule exhausted");
    }

    @Test
    void aJumpPastSeveralFramesDrainsOneClaimPerWindow() {
        long[] schedule = {55L, 62L};
        int next = 0;
        assertTrue(ModPrograms.celtium$chainInputPatchDue(schedule, next, 1000L));
        next++;
        assertTrue(ModPrograms.celtium$chainInputPatchDue(schedule, next, 1000L),
                "a frame that jumps past two named frames leaves the second due for the next window, "
                        + "so each sample is its own window's read");
        next++;
        assertFalse(ModPrograms.celtium$chainInputPatchDue(schedule, next, 1000L));
    }
}
