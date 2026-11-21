package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudP0CensusTest {

    /** The state a plain HUD draw is captured under: blend on, legacy factors, no depth, full RGB mask. */
    private static int[] overState() {
        int[] state = new int[16];
        state[0] = 1;
        state[1] = 6; // SRC_ALPHA
        state[2] = 7; // ONE_MINUS_SRC_ALPHA
        state[9] = 1; // ONE
        state[10] = 7; // ONE_MINUS_SRC_ALPHA
        state[11] = 0x7; // the window target's full mask, alpha write dropped
        state[12] = 0; // ADD
        state[13] = 0; // ADD
        return state;
    }

    @Test
    void theLegacyHudBlendIsOverClassUnderBothAlphaReadings() {
        assertEquals(HudP0Census.OVER, HudP0Census.classify(overState(), true, false));
        int[] oneZero = overState();
        oneZero[10] = 0; // ONE / ZERO alpha pair
        assertEquals(HudP0Census.OVER, HudP0Census.classify(oneZero, true, false));
        int[] fullMask = overState();
        fullMask[11] = 0xF; // an off-screen target's un-dropped mask is equally full
        assertEquals(HudP0Census.OVER, HudP0Census.classify(fullMask, true, false));
    }

    @Test
    void blendOffIsReplaceClassAndAnyOtherBlendIsLive() {
        int[] replace = overState();
        replace[0] = 0;
        assertEquals(HudP0Census.REPLACE, HudP0Census.classify(replace, true, false));
        int[] additive = overState();
        additive[2] = 1; // ONE destination: additive, not over
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(additive, true, false));
        int[] subtract = overState();
        subtract[12] = 3; // REVERSE_SUBTRACT equation
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(subtract, true, false));
    }

    @Test
    void depthMaskTargetAndSampledImageEachExcludeOnTheirOwn() {
        int[] depthTest = overState();
        depthTest[3] = 1;
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(depthTest, true, false));
        int[] depthWrite = overState();
        depthWrite[4] = 1;
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(depthWrite, true, false));
        int[] partialMask = overState();
        partialMask[11] = 0x3; // red+green only
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(partialMask, true, false));
        int[] noMask = overState();
        noMask[11] = 0;
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(noMask, true, false));
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(overState(), false, false));
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(overState(), true, true));
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(null, true, false));
        assertEquals(HudP0Census.LIVE, HudP0Census.classify(new int[4], true, false));
    }

    @Test
    void theFrameCountersNameEveryExclusionDimensionIndependently() {
        HudP0Census.Frame frame = new HudP0Census.Frame();
        int[] depthReplace = overState();
        depthReplace[0] = 0;
        depthReplace[3] = 1; // replace-class AND depth-excluded: both counters move, category is live
        frame.note(depthReplace, true, false, 2, 8, 256L);
        frame.note(overState(), false, true, 1, 4, 96L);
        assertEquals(2, frame.groups);
        assertEquals(3, frame.batches);
        assertEquals(12, frame.vertices);
        assertEquals(352L, frame.vertexBytes);
        assertEquals(1, frame.depthExcluded);
        assertEquals(1, frame.blendOff);
        assertEquals(1, frame.blendOver);
        assertEquals(1, frame.offTarget);
        assertEquals(1, frame.samplesTarget);
        assertEquals(0, frame.cacheable());
    }

    @Test
    void runsMergeOverRunsSplitOnLiveAndIsolateReplaceGroups() {
        HudP0Census.Frame frame = new HudP0Census.Frame();
        int[] replace = overState();
        replace[0] = 0;
        int[] live = overState();
        live[3] = 1;
        // O O L O R R O
        frame.note(overState(), true, false, 1, 4, 128L);
        frame.note(overState(), true, false, 1, 4, 128L);
        frame.note(live, true, false, 1, 4, 128L);
        frame.note(overState(), true, false, 1, 4, 128L);
        frame.note(replace, true, false, 1, 4, 128L);
        frame.note(replace, true, false, 1, 4, 128L);
        frame.note(overState(), true, false, 1, 4, 128L);
        assertEquals(6, frame.cacheable());
        assertEquals(4, frame.cacheableStrict());
        assertEquals(5, frame.runs());
        assertEquals(3, frame.runsStrict());
    }

    @Test
    void theFlushRegressionRecoversAKnownSlopeAndIntercept() {
        HudP0Census.FlushRegression regression = new HudP0Census.FlushRegression();
        for (int groups = 0; groups <= 20; groups++) {
            regression.note(groups, 2_000L + 500L * groups);
        }
        assertEquals(21, regression.windows());
        assertEquals(210, regression.totalGroups());
        assertEquals(500.0, regression.slopeNanosPerGroup(), 1e-6);
        assertEquals(2_000.0, regression.interceptNanos(), 1e-6);
    }

    @Test
    void theFlushRegressionRefusesSamplesThatCannotSupportAFit() {
        HudP0Census.FlushRegression regression = new HudP0Census.FlushRegression();
        assertTrue(Double.isNaN(regression.slopeNanosPerGroup()));
        regression.note(5, 1_000L);
        assertTrue(Double.isNaN(regression.slopeNanosPerGroup()));
        regression.note(5, 3_000L); // same group count: no variance to fit against
        assertTrue(Double.isNaN(regression.slopeNanosPerGroup()));
        assertTrue(Double.isNaN(regression.interceptNanos()));
    }
}
