package org.soutalab.celtium.forge.terrain;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;
import org.soutalab.celtium.vulkan.terrain.lane1.SectionGeometryRaw;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void toRawCarriesTheCornerLightColumnInStoreLayout() throws Exception {
        // R-107, ruling #15648 condition 1: the driver's toRaw handoff must carry the
        // per-corner light column into the raw carrier, in the store's layout - every
        // axis quad's two words in layer order, then every general quad's.
        MeshBuffers buffers = new MeshBuffers();
        buffers.reset(false);
        PackedAxisQuad quad = PackedAxisQuad.of(0, 0, 0, 1, false, 1, 1, 3, 0, 0, 15, 15,
                255, 0, 0, 0, 0);
        buffers.addAxisQuad(0, quad, (short) 0, 1000, 1001);
        buffers.addAxisQuad(0, quad, (short) 0, 1010, 1011);
        buffers.addAxisQuad(1, quad, (short) 0, 1100, 1101);
        float[] positions = new float[12];
        int[] uv = new int[4];
        buffers.addGeneralQuad(0, positions, 1, 0, 0, 0, uv, 2000, 2001);
        buffers.addGeneralQuad(2, positions, 1, 0, 0, 0, uv, 2200, 2201);

        SectionGeometryRaw raw = toRaw(buffers);
        assertEquals(3, raw.axisQuadCount());
        assertEquals(2, raw.generalQuadCount());
        assertArrayEquals(new int[]{1000, 1001, 1010, 1011, 1100, 1101, 2000, 2001, 2200, 2201},
                raw.cornerLight(), "axis layers first, then general layers, two words per quad");
        assertArrayEquals(buffers.cornerLightColumn(), raw.cornerLight(),
                "the carrier holds the buffers' concatenated column itself");
    }

    private static SectionGeometryRaw toRaw(MeshBuffers buffers) throws Exception {
        Method toRaw = CeltiumTerrainDriver.class.getDeclaredMethod("toRaw", MeshBuffers.class);
        toRaw.setAccessible(true);
        return (SectionGeometryRaw) toRaw.invoke(null, buffers);
    }

    private static byte[] patterned(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 31 + (i >>> 3));
        }
        return data;
    }
}
