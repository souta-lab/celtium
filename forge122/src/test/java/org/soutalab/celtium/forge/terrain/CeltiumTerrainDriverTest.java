package org.soutalab.celtium.forge.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S2-b-perf CPU-1/CPU-2 pieces of the terrain driver that are testable without a world:
 * the tick gate's state machine (the diff and the light sweep are due only when the world
 * time advances) and the word-parallel light fingerprint's contract (same bytes, same value;
 * a changed byte changes the value - it is an internal change detector, nothing more).
 */
class CeltiumTerrainDriverTest {

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
