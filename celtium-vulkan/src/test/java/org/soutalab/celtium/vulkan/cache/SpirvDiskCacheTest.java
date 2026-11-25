package org.soutalab.celtium.vulkan.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SpirvDiskCacheTest {

    private static byte[] syntheticSpirv(int seed) {
        ByteBuffer buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(SpirvDiskCache.SPIRV_MAGIC);
        buf.putInt(0x00010500); // SPIR-V 1.5
        buf.putInt(0);          // generator
        buf.putInt(16);         // bound
        buf.putInt(0);          // schema
        buf.putInt(seed);       // payload word
        return buf.array();
    }

    @Test
    void roundTripMemoryAndDiskHitsAcrossInstances(@TempDir Path dir) throws Exception {
        SpirvDiskCache writer = new SpirvDiskCache(dir, "translator-v1");
        String key = writer.computeKey("vert", "-O", "#version 450\nvoid main(){}");
        assertEquals(64, key.length());
        assertNull(writer.get(key));
        assertEquals(1L, writer.misses());

        byte[] spv = syntheticSpirv(0x12345678);
        writer.put(key, spv);
        assertEquals(1L, writer.writes());
        assertTrue(Files.isRegularFile(writer.pathForKey(key)));

        // Same instance -> L1 memory hit
        assertArrayEquals(spv, writer.get(key));
        assertEquals(1L, writer.memoryHits());
        assertEquals(0L, writer.diskHits());

        // Fresh instance -> L2 disk hit, then L1 memory hit
        SpirvDiskCache reader = new SpirvDiskCache(dir, "translator-v1");
        assertArrayEquals(spv, reader.get(key));
        assertEquals(1L, reader.diskHits());
        assertArrayEquals(spv, reader.get(key));
        assertEquals(1L, reader.memoryHits());
    }

    @Test
    void keysDifferByStageFlagsTranslatorVersionAndSource(@TempDir Path dir) {
        SpirvDiskCache c1 = new SpirvDiskCache(dir, "v1");
        SpirvDiskCache c2 = new SpirvDiskCache(dir, "v2");

        String kBase = c1.computeKey("frag", "-O", "void main(){}");
        assertEquals(kBase, c1.computeKey("frag", "-O", "void main(){}"));
        assertNotEquals(kBase, c1.computeKey("vert", "-O", "void main(){}"));
        assertNotEquals(kBase, c1.computeKey("frag", "-O0", "void main(){}"));
        assertNotEquals(kBase, c1.computeKey("frag", "-O", "void main(){ }"));
        assertNotEquals(kBase, c2.computeKey("frag", "-O", "void main(){}"));
    }

    @Test
    void corruptDiskEntryDegradesCleanlyToMiss(@TempDir Path dir) throws Exception {
        SpirvDiskCache writer = new SpirvDiskCache(dir, "v1");
        String key = writer.computeKey("comp", "", "void main(){}");
        writer.put(key, syntheticSpirv(42));

        // Corrupt a payload byte on disk
        Path file = writer.pathForKey(key);
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 0x55;
        Files.write(file, bytes);

        SpirvDiskCache reader = new SpirvDiskCache(dir, "v1");
        assertNull(reader.get(key));
        assertEquals(1L, reader.corruptRejections());
        assertEquals(1L, reader.misses());
    }

    @Test
    void rejectsNonSpirvPayloads(@TempDir Path dir) {
        SpirvDiskCache cache = new SpirvDiskCache(dir, "v1");
        String key = cache.computeKey("frag", "", "void main(){}");
        assertThrows(IllegalArgumentException.class, () -> cache.put(key, new byte[8]));
        assertThrows(IllegalArgumentException.class, () -> cache.put(key, new byte[20])); // wrong magic
    }
}
