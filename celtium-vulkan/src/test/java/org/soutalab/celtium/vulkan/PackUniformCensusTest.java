package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The item-678 census' counting, driven through its Ledger directly: the window line carries the
 * rates the Layer B judgement reads (claims against regions, walks against claims, the set split),
 * the first pack of a program files its shape exactly once, and the lane-diff histogram names the
 * 16-byte lanes that actually changed between consecutive packs - including the two readings that
 * are easy to get wrong: a cache-served pack (identical bytes) files zero changed lanes, and the
 * previous block survives a window reset, because the diff is between packs, not windows.
 */
class PackUniformCensusTest {

    private static ByteBuffer block(int bytes, int... edits) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes);
        for (int i = 0; i + 1 < edits.length; i += 2) {
            buffer.put(edits[i], (byte) edits[i + 1]);
        }
        return buffer;
    }

    @Test void windowLineCarriesTheCountersAndResetsThem() {
        PackUniformCensus.Ledger ledger = new PackUniformCensus.Ledger(false);
        ledger.noteRegion(true);
        ledger.noteRegion(true);
        ledger.noteRegion(false);
        ledger.noteSkippedRegion();
        ledger.noteSlotClaim();
        ledger.noteSetHit();
        ledger.noteSetMiss();
        ledger.noteSetAlloc();
        List<String> lines = ledger.windowLines(200);
        assertEquals(1, lines.size());
        assertEquals("CELTIUM_PACK_UNIFORM frames=200 regionsShadow=2 regionsGbuffers=1"
                + " slotClaims=1 packWalks=0 setAllocs=1 setHits=1 setMisses=1 skippedRegions=1",
                lines.get(0));
        assertTrue(ledger.windowLines(200).isEmpty(), "an idle window prints nothing");
    }

    @Test void firstPackFilesTheProgramShapeOnce() {
        PackUniformCensus.Ledger ledger = new PackUniformCensus.Ledger(false);
        Object key = new Object();
        String first = ledger.notePack(key, 7, 23, 1568, true, 12, 2, block(1568));
        assertEquals("CELTIUM_PACK_UNIFORM_PROGRAM program=7 members=23 blockBytes=1568"
                + " route=ubo samplers=12 images=2", first);
        assertNull(ledger.notePack(key, 7, 23, 1568, true, 12, 2, block(1568)),
                "the shape line is first-sight only");
        assertEquals(2, ledger.packWalks());
    }

    @Test void laneHistogramNamesOnlyTheChangedLanes() {
        PackUniformCensus.Ledger ledger = new PackUniformCensus.Ledger(true);
        Object key = new Object();
        // 48 bytes = lanes 0, 1, 2. First pack establishes the baseline; nothing is filed for it.
        ledger.notePack(key, 9, 3, 48, false, 0, 0, block(48));
        // Second pack edits lane 0 (byte 3) and lane 2 (byte 40); lane 1 is untouched.
        ledger.notePack(key, 9, 3, 48, false, 0, 0, block(48, 3, 0x7f, 40, 0x01));
        // Third pack is byte-identical to the second: the cache-served reading, zero new changes.
        ledger.notePack(key, 9, 3, 48, false, 0, 0, block(48, 3, 0x7f, 40, 0x01));
        List<String> lines = ledger.windowLines(200);
        assertEquals(2, lines.size());
        assertEquals("CELTIUM_PACK_UNIFORM_LANES program=9 blockBytes=48 changed{0=1 2=1}",
                lines.get(1));
    }

    @Test void laneDiffSurvivesTheWindowReset() {
        PackUniformCensus.Ledger ledger = new PackUniformCensus.Ledger(true);
        Object key = new Object();
        ledger.notePack(key, 11, 1, 32, false, 0, 0, block(32));
        ledger.noteRegion(false);
        assertEquals(1, ledger.windowLines(200).size(), "no lane moved yet, so no lane line");
        // The next pack arrives in the following window; the diff is still against the last pack.
        ledger.notePack(key, 11, 1, 32, false, 0, 0, block(32, 20, 0x55));
        List<String> lines = ledger.windowLines(200);
        assertEquals(2, lines.size());
        assertEquals("CELTIUM_PACK_UNIFORM_LANES program=11 blockBytes=32 changed{1=1}",
                lines.get(1));
    }

    @Test void pipelineCountersStaySeparatePerPipelineAndRoute() {
        PackUniformCensus.Ledger ledger = new PackUniformCensus.Ledger(false);
        Object terrain = new Object();
        Object nativePass = new Object();
        ledger.notePipelineSetHit(terrain, "terrain");
        ledger.notePipelineSetHit(terrain, "terrain");
        ledger.notePipelineSetMiss(terrain, "terrain");
        ledger.notePipelineSetAlloc(terrain, "terrain", 1);
        ledger.notePipelineSetHit(nativePass, "native");
        List<String> lines = ledger.windowLines(200);
        assertEquals(2, lines.size(), "no aggregate activity, so only the pipeline lines print");
        assertEquals("CELTIUM_PACK_UNIFORM_PIPELINE pipeline=1 route=terrain sets=1 maxSets=1"
                + " hits=2 misses=1 allocs=1 allocFailures=0 capFailures=0", lines.get(0));
        assertEquals("CELTIUM_PACK_UNIFORM_PIPELINE pipeline=2 route=native sets=0 maxSets=0"
                + " hits=1 misses=0 allocs=0 allocFailures=0 capFailures=0", lines.get(1));
    }

    @Test void pipelinePopulationSurvivesTheWindowResetWhileCountersReset() {
        PackUniformCensus.Ledger ledger = new PackUniformCensus.Ledger(false);
        Object pipeline = new Object();
        ledger.notePipelineSetAlloc(pipeline, "mod", 5);
        ledger.notePipelineSetAlloc(pipeline, "mod", 9);
        ledger.notePipelineSetAlloc(pipeline, "mod", 7);
        List<String> first = ledger.windowLines(200);
        assertEquals("CELTIUM_PACK_UNIFORM_PIPELINE pipeline=1 route=mod sets=7 maxSets=9"
                + " hits=0 misses=0 allocs=3 allocFailures=0 capFailures=0", first.get(0));
        ledger.notePipelineSetHit(pipeline, "mod");
        List<String> second = ledger.windowLines(200);
        assertEquals("CELTIUM_PACK_UNIFORM_PIPELINE pipeline=1 route=mod sets=7 maxSets=7"
                + " hits=1 misses=0 allocs=0 allocFailures=0 capFailures=0", second.get(0),
                "population is state and is kept; the window counters and max restart");
        assertTrue(ledger.windowLines(200).isEmpty(), "an idle pipeline prints nothing");
    }

    @Test void capAndPoolFailuresLandInSeparateCounters() {
        PackUniformCensus.Ledger ledger = new PackUniformCensus.Ledger(false);
        Object pipeline = new Object();
        ledger.notePipelineAllocFailure(pipeline, "terrain", true);
        ledger.notePipelineAllocFailure(pipeline, "terrain", true);
        ledger.notePipelineAllocFailure(pipeline, "terrain", false);
        List<String> lines = ledger.windowLines(200);
        assertEquals(1, lines.size());
        assertEquals("CELTIUM_PACK_UNIFORM_PIPELINE pipeline=1 route=terrain sets=0 maxSets=0"
                + " hits=0 misses=0 allocs=0 allocFailures=1 capFailures=2", lines.get(0));
    }

    @Test void failureOnlyWindowStillPrintsThePipelineLine() {
        PackUniformCensus.Ledger ledger = new PackUniformCensus.Ledger(false);
        Object quiet = new Object();
        Object failing = new Object();
        ledger.notePipelineSetAlloc(quiet, "mod", 3);
        ledger.windowLines(200);
        ledger.notePipelineAllocFailure(failing, "native", false);
        List<String> lines = ledger.windowLines(200);
        assertEquals(1, lines.size(), "the quiet pipeline stays silent; the failure alone opens a line");
        assertTrue(lines.get(0).startsWith("CELTIUM_PACK_UNIFORM_PIPELINE pipeline=2 route=native"),
                lines.get(0));
    }

    @Test void laneCensusOffFilesNoLaneState() {
        PackUniformCensus.Ledger ledger = new PackUniformCensus.Ledger(false);
        Object key = new Object();
        ledger.notePack(key, 13, 1, 32, false, 0, 0, block(32));
        ledger.notePack(key, 13, 1, 32, false, 0, 0, block(32, 0, 0x01));
        List<String> lines = ledger.windowLines(200);
        assertEquals(1, lines.size(), "counters still print; only the lane lines are the flag's");
    }
}
