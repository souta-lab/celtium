package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImmediateGroupSplitCensusTest {
    private ImmediateGroupSplitCensus census;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        this.census = new ImmediateGroupSplitCensus();
    }

    @Test
    void thePredicateTermsAreClassifiedInTheOrderTheMergeTestsThem() {
        // Earlier terms win: a batch that changed format, texture and state key is attributed to the format only,
        // because that is the term the merge predicate short-circuits on.
        assertEquals(ImmediateGroupSplitCensus.Reason.FORMAT, ImmediateGroupSplitCensus.classify(
                false, false, true, true, true, false, true, true, true, true, true, true, true, true, true, true, true));
        // A pass boundary is named as a pass boundary, not as a descriptor pair.
        assertEquals(ImmediateGroupSplitCensus.Reason.PASS, ImmediateGroupSplitCensus.classify(
                false, false, false, false, false, false, false, false, false, false, false, false, false,
                false, false, false, false));
        assertEquals(ImmediateGroupSplitCensus.Reason.PREPARED, ImmediateGroupSplitCensus.classify(
                false, false, false, false, false, true, false, false, false, true, true, true, true, true, true, true, true));
        assertEquals(ImmediateGroupSplitCensus.Reason.SCISSOR, ImmediateGroupSplitCensus.classify(
                false, false, false, false, false, true, true, true, true, true, true, true, true, true, true, true, true));
    }

    @Test
    void retainedGeometryAndTheFirstGroupOfAWindowAreNotSplits() {
        assertEquals(ImmediateGroupSplitCensus.Reason.RETAINED, ImmediateGroupSplitCensus.classify(
                true, true, true, true, true, false, true, true, true, true, true, true, true, true, true, true, true));
        assertNull(ImmediateGroupSplitCensus.classify(
                false, true, true, true, true, false, true, true, true, true, true, true, true, true, true, true, true));
        this.census.note(null);
        assertEquals(0, this.census.total());
    }

    @Test
    void aMatrixThatCannotBeBakedIsNamedSeparatelyFromOneThatMoved() {
        assertEquals(ImmediateGroupSplitCensus.Reason.MATRIX_NOT_BAKABLE, ImmediateGroupSplitCensus.classify(
                false, false, false, false, false, true, false, false, false, false, false, true, false,
                false, false, false, false));
        assertEquals(ImmediateGroupSplitCensus.Reason.MATRIX, ImmediateGroupSplitCensus.classify(
                false, false, false, false, false, true, false, false, false, false, true, false, false,
                false, false, false, false));
        assertEquals(ImmediateGroupSplitCensus.Reason.MATRIX, ImmediateGroupSplitCensus.classify(
                false, false, false, false, false, true, false, false, false, false, false, false, true,
                false, false, false, false));
    }

    @Test
    void everyRemainingTermReachesItsOwnReason() {
        Object[][] cases = {
            {ImmediateGroupSplitCensus.Reason.TEXTURE, 3},
            {ImmediateGroupSplitCensus.Reason.STATE_KEY, 4},
            {ImmediateGroupSplitCensus.Reason.SCISSOR, 6},
            {ImmediateGroupSplitCensus.Reason.COLOUR, 13},
            {ImmediateGroupSplitCensus.Reason.TEXTURE_AFFINE, 14},
            {ImmediateGroupSplitCensus.Reason.FOG, 15},
            {ImmediateGroupSplitCensus.Reason.LIGHT, 16},
        };
        for (Object[] testCase : cases) {
            boolean[] facts = new boolean[17];
            facts[5] = true; // samePass
            facts[(Integer) testCase[1]] = true;
            assertEquals(testCase[0], ImmediateGroupSplitCensus.classify(facts[0], facts[1], facts[2], facts[3],
                    facts[4], facts[5], facts[6], facts[7], facts[8], facts[9], facts[10], facts[11], facts[12],
                    facts[13], facts[14], facts[15], facts[16]));
        }
    }

    @Test
    void summaryIsBoundedAndOmitsCausesThatNeverFired() {
        this.census.note(ImmediateGroupSplitCensus.Reason.TEXTURE);
        this.census.note(ImmediateGroupSplitCensus.Reason.TEXTURE);
        this.census.note(ImmediateGroupSplitCensus.Reason.STATE_KEY);
        String summary = this.census.summary();
        assertEquals("total=3 texture=2 state_key=1", summary);
        assertEquals(1, this.census.count(ImmediateGroupSplitCensus.Reason.STATE_KEY));
        assertEquals(0, this.census.count(ImmediateGroupSplitCensus.Reason.FOG));
    }

    @Test
    void aFewAffinesUsedOverAndOverIsTellApartFromANewOneEveryTime() {
        // Two alternating values: the game is switching states between draws.
        this.census.noteTextureAffine(null);
        this.census.noteTextureAffine(new float[] {1, 0, 0, 1, 0.25f, 0});
        this.census.noteTextureAffine(null);
        this.census.noteTextureAffine(new float[] {1, 0, 0, 1, 0.25f, 0});
        assertEquals("affineSamples=4 affineDistinct=2 affineRepeats=2 affineNonIdentity=2 affineTx=[0.250..0.250] affineTy=[0.000..0.000] affineScale=[1.000..1.000]", this.census.affineSummary());
    }

    @Test
    void aDistinctAffineEveryTimeSaturatesTheTableInsteadOfGrowing() {
        for (int i = 1; i <= 40; i++) {
            this.census.noteTextureAffine(new float[] {1, 0, 0, 1, i, 0});
        }
        // Bounded: the table stops accepting new values, and the report says so rather than implying 8 is the count.
        assertTrue(this.census.affineSummary().startsWith("affineSamples=40 affineDistinct+=8 affineRepeats=0"),
                this.census.affineSummary());
        // The bounds are what separates a drifting translate from a genuine per-draw one.
        assertTrue(this.census.affineSummary().contains("affineTx=[1.000..40.000]"), this.census.affineSummary());
    }

    @Test
    void theIdentityIsAValueAndTheCapturedArrayIsCopied() {
        float[] live = new float[] {1, 0, 0, 1, 0.5f, 0};
        this.census.noteTextureAffine(live);
        live[4] = 9.0f; // the caller's scratch is reused for the next batch
        this.census.noteTextureAffine(new float[] {1, 0, 0, 1, 0.5f, 0});
        assertTrue(this.census.affineSummary().startsWith("affineSamples=2 affineDistinct=1 affineRepeats=1"),
                this.census.affineSummary());
        this.census.noteTextureAffine(null);
        this.census.noteTextureAffine(new float[0]);
        assertTrue(this.census.affineSummary().startsWith("affineSamples=4 affineDistinct=2 affineRepeats=2"),
                this.census.affineSummary());
    }
}
