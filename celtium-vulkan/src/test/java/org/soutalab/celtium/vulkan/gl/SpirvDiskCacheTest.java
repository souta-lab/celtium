package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpirvDiskCacheTest {

    private static final String SOURCE = "#version 450\nvoid main() { }\n";
    private static final byte[] SPIRV = {3, 2, 35, 7, 0, 0, 1, 0, 11, 22, 33, 44};

    @TempDir
    Path dir;

    @BeforeEach
    void pointTheCacheAtTheTempDir() {
        System.setProperty("celtium.spirvCache.dir", this.dir.toString());
        System.clearProperty("celtium.spirvCache.enabled");
    }

    @AfterEach
    void restoreProperties() {
        System.clearProperty("celtium.spirvCache.dir");
        System.clearProperty("celtium.spirvCache.enabled");
    }

    private Path entryFile(String source, boolean vertex) {
        return this.dir.resolve(SpirvDiskCache.keyFor(source, vertex) + ".spvbin");
    }

    @Test
    void missCompilesOnceAndThenHits() {
        AtomicInteger compiles = new AtomicInteger();
        Supplier<byte[]> compiler = () -> {
            compiles.incrementAndGet();
            return SPIRV.clone();
        };
        assertNull(SpirvDiskCache.load(SOURCE, true));
        assertArrayEquals(SPIRV, SpirvDiskCache.loadOrCompile(SOURCE, true, compiler));
        assertEquals(1, compiles.get());
        // The second call is served from disk: the compiler is not invoked again.
        assertArrayEquals(SPIRV, SpirvDiskCache.loadOrCompile(SOURCE, true, compiler));
        assertEquals(1, compiles.get());
        assertArrayEquals(SPIRV, SpirvDiskCache.load(SOURCE, true));
    }

    @Test
    void stagesAndSourcesHaveSeparateEntries() {
        assertNotEquals(SpirvDiskCache.keyFor(SOURCE, true), SpirvDiskCache.keyFor(SOURCE, false));
        assertNotEquals(SpirvDiskCache.keyFor(SOURCE, true), SpirvDiskCache.keyFor(SOURCE + " ", true));
        SpirvDiskCache.store(SOURCE, true, SPIRV);
        assertNull(SpirvDiskCache.load(SOURCE, false), "the fragment stage must not see the vertex entry");
        assertNull(SpirvDiskCache.load(SOURCE + " ", true), "a different source must not hit");
        byte[] fragment = {9, 9, 9, 9};
        SpirvDiskCache.store(SOURCE, false, fragment);
        assertArrayEquals(SPIRV, SpirvDiskCache.load(SOURCE, true));
        assertArrayEquals(fragment, SpirvDiskCache.load(SOURCE, false));
    }

    @Test
    void corruptEntryFallsBackToCompileAndIsOverwritten() throws IOException {
        SpirvDiskCache.store(SOURCE, true, SPIRV);
        Files.write(entryFile(SOURCE, true), new byte[]{1, 2, 3});
        AtomicInteger compiles = new AtomicInteger();
        byte[] fresh = {5, 6, 7, 8, 9};
        byte[] result = SpirvDiskCache.loadOrCompile(SOURCE, true, () -> {
            compiles.incrementAndGet();
            return fresh.clone();
        });
        assertEquals(1, compiles.get(), "a corrupt entry must fall back to compiling");
        assertArrayEquals(fresh, result);
        assertArrayEquals(fresh, SpirvDiskCache.load(SOURCE, true), "the fresh compile replaces the corrupt entry");
    }

    @Test
    void tamperedPayloadIsRejected() throws IOException {
        SpirvDiskCache.store(SOURCE, true, SPIRV);
        Path file = entryFile(SOURCE, true);
        byte[] raw = Files.readAllBytes(file);
        raw[12] ^= 0xFF; // first payload byte; the trailing checksum no longer matches
        Files.write(file, raw);
        assertNull(SpirvDiskCache.load(SOURCE, true));
        assertTrue(Files.notExists(file), "a corrupt entry is evicted, not left to fail forever");
    }

    @Test
    void truncatedEntryIsRejected() throws IOException {
        SpirvDiskCache.store(SOURCE, true, SPIRV);
        Path file = entryFile(SOURCE, true);
        byte[] raw = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(raw, raw.length - 5));
        assertNull(SpirvDiskCache.load(SOURCE, true));
    }

    @Test
    void disabledCacheNeitherStoresNorLoads() {
        System.setProperty("celtium.spirvCache.enabled", "false");
        SpirvDiskCache.store(SOURCE, true, SPIRV);
        assertTrue(Files.notExists(entryFile(SOURCE, true)));
        assertNull(SpirvDiskCache.load(SOURCE, true));
        AtomicInteger compiles = new AtomicInteger();
        SpirvDiskCache.loadOrCompile(SOURCE, true, () -> {
            compiles.incrementAndGet();
            return SPIRV.clone();
        });
        SpirvDiskCache.loadOrCompile(SOURCE, true, () -> {
            compiles.incrementAndGet();
            return SPIRV.clone();
        });
        assertEquals(2, compiles.get(), "with the cache off every call compiles, as before the cache existed");
    }
}
