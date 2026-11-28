package org.soutalab.celtium.forge.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S2-b-perf CPU-1/CPU-2 pieces of the terrain driver that are testable without a world:
 * the tick gate's state machine (the diff and the light sweep are due only when the world
 * time advances) and the word-parallel light fingerprint's contract (same bytes, same value;
 * a changed byte changes the value - it is an internal change detector, nothing more).
 */
class CeltiumTerrainDriverTest {

    @Test
    void setupStatsDrainLineReportsTheWindowAndResets() {
        CeltiumTerrainDriver.SetupStats stats = new CeltiumTerrainDriver.SetupStats();
        assertNull(stats.drainLine(0L, 0L), "no frames counted: no line");
        stats.noteFrame(true, 3, 2, 1, 10, 4);
        stats.noteFrame(false, 5, 1, 0, 20, 6);
        stats.noteMarkArea(7);
        stats.noteMarkArea(0);
        stats.noteTerrainUpdateAll();
        String line = stats.drainLine(11L, 2_500_000L);
        assertTrue(line.contains("frames=2"), line);
        assertTrue(line.contains("tickFrames=1"), line);
        assertTrue(line.contains("snapshots=8"), line);
        assertTrue(line.contains("snapshotsMax=5"), line);
        assertTrue(line.contains("dirtyAvg=15"), line);
        assertTrue(line.contains("dirtyMax=20"), line);
        assertTrue(line.contains("pendingAvg=5"), line);
        assertTrue(line.contains("uploads=3"), line);
        assertTrue(line.contains("uploadsMax=2"), line);
        assertTrue(line.contains("refusedUploads=1"), line);
        assertTrue(line.contains("markAreaCalls=2"), line);
        assertTrue(line.contains("markAreaAdded=7"), line);
        assertTrue(line.contains("terrainUpdateAllCalls=1"), line);
        assertTrue(line.contains("tintCells=11"), line);
        assertTrue(line.contains("tintMs=2.500"), line);
        assertNull(stats.drainLine(0L, 0L), "the drain reset the window");
    }

    @Test
    void tickGateAdvancesOnlyWhenTheWorldTimeChanges() {
        CeltiumTerrainDriver.TickGate gate = new CeltiumTerrainDriver.TickGate();
        assertTrue(gate.advance(100L), "the first frame of a world always syncs");
        assertFalse(gate.advance(100L), "same tick: nothing new can have loaded or relit");
        assertFalse(gate.advance(100L));
        assertTrue(gate.advance(101L));
        assertFalse(gate.advance(101L));
        assertTrue(gate.advance(99L), "time moving backwards (set / new world) also syncs");
        gate.reset();
        assertTrue(gate.advance(99L), "a reset gate accepts the next frame unconditionally");
    }

    @Test
    void fingerprintIsDeterministicForEqualBytes() {
        byte[] data = patterned(2048);
        assertEquals(CeltiumTerrainDriver.fingerprintWords(data),
                CeltiumTerrainDriver.fingerprintWords(patterned(2048)));
        assertEquals(CeltiumTerrainDriver.fingerprintWords(new byte[2048]),
                CeltiumTerrainDriver.fingerprintWords(new byte[2048]));
    }

    @Test
    void fingerprintChangesWithAnySingleByte() {
        byte[] base = patterned(2048);
        int expected = CeltiumTerrainDriver.fingerprintWords(base);
        // One probe per accumulator lane, the word seams and the very last byte.
        for (int position : new int[]{0, 1, 7, 8, 9, 100, 511, 1024, 2040, 2047}) {
            byte[] changed = base.clone();
            changed[position] ^= 0x5A;
            assertNotEquals(expected, CeltiumTerrainDriver.fingerprintWords(changed),
                    "byte " + position + " changed but the fingerprint did not");
        }
    }

    @Test
    void fingerprintHandlesTheTailBytes() {
        // Lengths that are not whole words take the tail path; changes there count too.
        byte[] base = patterned(2051);
        int expected = CeltiumTerrainDriver.fingerprintWords(base);
        assertEquals(expected, CeltiumTerrainDriver.fingerprintWords(patterned(2051)));
        for (int position : new int[]{2048, 2049, 2050}) {
            byte[] changed = base.clone();
            changed[position] ^= 0x33;
            assertNotEquals(expected, CeltiumTerrainDriver.fingerprintWords(changed),
                    "tail byte " + position + " changed but the fingerprint did not");
        }
        assertNotEquals(CeltiumTerrainDriver.fingerprintWords(patterned(2048)),
                CeltiumTerrainDriver.fingerprintWords(patterned(2051)),
                "different lengths must not collide on this pattern");
    }

    private static byte[] patterned(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 31 + (i >>> 3));
        }
        return data;
    }
}
