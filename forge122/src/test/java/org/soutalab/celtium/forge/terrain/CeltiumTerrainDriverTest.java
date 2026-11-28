package org.soutalab.celtium.forge.terrain;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    void reloadMarksEveryResidentSectionDirty() {
        // P0-1: reload() is the one remaining all-sections trigger - a resource
        // reload or an explicit settings hook, never the vanilla notification.
        CeltiumTerrainDriver driver = new CeltiumTerrainDriver();
        LongOpenHashSet resident = longSet(driver, "resident");
        resident.add(key(0, 0, 0));
        resident.add(key(3, 1, -2));
        driver.reload();
        LongOpenHashSet dirty = longSet(driver, "dirty");
        assertEquals(2, dirty.size());
        assertTrue(dirty.contains(key(0, 0, 0)));
        assertTrue(dirty.contains(key(3, 1, -2)));
    }

    @Test
    void vanillaNotificationNoLongerReachesTheDriver() throws Exception {
        // P0-1's cut, pinned at the source: the mixin must not observe
        // setDisplayListEntitiesDirty, the driver must not offer the old
        // scheduleTerrainUpdate entry, and dirty.addAll(resident) survives
        // in reload() only.
        String mixin = readMainSource(
                "org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.java");
        assertFalse(mixin.contains("setDisplayListEntitiesDirty"),
                "the notification injection is gone");
        assertFalse(mixin.contains("scheduleTerrainUpdate"),
                "the mixin no longer calls the all-sections update");
        String driver = readMainSource(
                "org/soutalab/celtium/forge/terrain/CeltiumTerrainDriver.java");
        assertFalse(driver.contains("scheduleTerrainUpdate"),
                "the notification entry point itself is gone");
        assertEquals(1, countOccurrences(driver, "dirty.addAll(resident)"),
                "the all-sections dirtying remains in reload() only");
        String screen = readMainSource(
                "org/soutalab/celtium/forge/ui/CeltiumVideoSettingsScreen.java");
        assertFalse(screen.contains("renderGlobal.setDisplayListEntitiesDirty()"),
                "the settings screen no longer pokes the vanilla notification");
        assertTrue(screen.contains("driver.reload()"),
                "the settings hook calls the driver's reload explicitly");
    }

    @Test
    void markAreaMidSectionAddsNoNeighbors() {
        CeltiumTerrainDriver driver = driverWithPresentShell();
        driver.scheduleRebuildForBlockArea(5, 5, 5, 5, 5, 5, false);
        LongOpenHashSet dirty = longSet(driver, "dirty");
        assertEquals(1, dirty.size());
        assertTrue(dirty.contains(key(0, 0, 0)));
    }

    @Test
    void markAreaLowEdgeAddsOnlyTheLowNeighbor() {
        CeltiumTerrainDriver driver = driverWithPresentShell();
        driver.scheduleRebuildForBlockArea(0, 5, 5, 0, 5, 5, false);
        LongOpenHashSet dirty = longSet(driver, "dirty");
        assertEquals(2, dirty.size());
        assertTrue(dirty.contains(key(0, 0, 0)));
        assertTrue(dirty.contains(key(-1, 0, 0)), "the touched low neighbor joins");
        assertFalse(dirty.contains(key(1, 0, 0)), "the untouched high neighbor does not");
    }

    @Test
    void markAreaHighEdgeAddsOnlyTheHighNeighbor() {
        CeltiumTerrainDriver driver = driverWithPresentShell();
        driver.scheduleRebuildForBlockArea(15, 5, 5, 15, 5, 5, false);
        LongOpenHashSet dirty = longSet(driver, "dirty");
        assertEquals(2, dirty.size());
        assertTrue(dirty.contains(key(0, 0, 0)));
        assertTrue(dirty.contains(key(1, 0, 0)), "the touched high neighbor joins");
        assertFalse(dirty.contains(key(-1, 0, 0)), "the untouched low neighbor does not");
    }

    @Test
    void markAreaCornerTouchesBothLowNeighborsAndTheDiagonal() {
        CeltiumTerrainDriver driver = driverWithPresentShell();
        driver.scheduleRebuildForBlockArea(0, 5, 0, 0, 5, 0, false);
        LongOpenHashSet dirty = longSet(driver, "dirty");
        assertEquals(4, dirty.size());
        assertTrue(dirty.contains(key(-1, 0, -1)), "both axes touch: the diagonal joins");
        assertFalse(dirty.contains(key(1, 0, 1)));
    }

    @Test
    void markAreaSkipsSectionsAlreadyQueued() {
        CeltiumTerrainDriver driver = driverWithPresentShell();
        LongOpenHashSet dirty = longSet(driver, "dirty");
        dirty.add(key(0, 0, 0));
        driver.scheduleRebuildForBlockArea(5, 5, 5, 5, 5, 5, false);
        assertEquals(1, dirty.size(), "already dirty: not re-queued");

        CeltiumTerrainDriver pendingDriver = driverWithPresentShell();
        @SuppressWarnings("unchecked")
        Map<Long, CompletableFuture<?>> pending =
                (Map<Long, CompletableFuture<?>>) field(pendingDriver, "pending");
        pending.put(key(0, 0, 0), CompletableFuture.completedFuture(null));
        pendingDriver.scheduleRebuildForBlockArea(5, 5, 5, 5, 5, 5, false);
        assertTrue(longSet(pendingDriver, "dirty").isEmpty(),
                "a build in flight is the rebuild: not re-queued");
    }

    @Test
    void markAreaClampsTheVerticalRange() {
        CeltiumTerrainDriver driver = driverWithPresentShell();
        // y=0 is the low edge of section y=0; the would-be neighbor y=-1 is clamped.
        driver.scheduleRebuildForBlockArea(5, 0, 5, 5, 0, 5, false);
        LongOpenHashSet dirty = longSet(driver, "dirty");
        assertEquals(1, dirty.size());
        assertTrue(dirty.contains(key(0, 0, 0)));
    }

    /** A driver whose present set covers sections -2..2 on x/z and 0..1 on y. */
    private static CeltiumTerrainDriver driverWithPresentShell() {
        CeltiumTerrainDriver driver = new CeltiumTerrainDriver();
        LongOpenHashSet present = longSet(driver, "present");
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    present.add(key(x, y, z));
                }
            }
        }
        return driver;
    }

    private static long key(int x, int y, int z) {
        try {
            Method key = CeltiumTerrainDriver.class.getDeclaredMethod("key", int.class, int.class, int.class);
            key.setAccessible(true);
            return (long) key.invoke(null, x, y, z);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static LongOpenHashSet longSet(Object target, String fieldName) {
        return (LongOpenHashSet) field(target, fieldName);
    }

    private static Object field(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static String readMainSource(String relative) throws Exception {
        Path moduleRelative = Path.of("src/main/java", relative);
        if (Files.exists(moduleRelative)) {
            return Files.readString(moduleRelative);
        }
        return Files.readString(Path.of("forge122/src/main/java", relative));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
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
