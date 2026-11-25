package org.soutalab.celtium.vulkan.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SectionDesc wire shape (SECTION-DESC-SPEC-2026-11-25): the pack layout is a
 * contract with the culling compute shader and the Tier-B mesh path, so a packing
 * slip must fail here, not in a leg.
 */
class SectionDescTest {

    @Test
    void packUnpackRoundTrips() {
        SectionDesc source = SectionDesc.builder(-3, 12, 250, 2)
                .slab(5, 123456, 7890)
                .bucketCounts(new int[]{1, 22, 333, 4444, 5, 66, 777})
                .flags(SectionDesc.FLAG_ANIMATED)
                .generation(987654321L)
                .sortedIndexBase(424242)
                .build();
        int[] words = new int[SectionDesc.WORDS];
        source.pack(words, 0);
        SectionDesc back = SectionDesc.unpack(words, 0);
        assertEquals(-3, back.sectionX);
        assertEquals(12, back.sectionY);
        assertEquals(250, back.sectionZ);
        assertEquals(5, back.slabId);
        assertEquals(123456, back.vertexOffset);
        assertEquals(7890, back.quadStart);
        assertEquals(2, back.passOrdinal);
        assertEquals(987654321L, back.generation);
        assertEquals(424242, back.sortedIndexBase);
        assertTrue(back.sorted());
        assertTrue((back.flags & SectionDesc.FLAG_ANIMATED) != 0);
        int[] expected = {1, 22, 333, 4444, 5, 66, 777};
        for (int i = 0; i < SectionDesc.BUCKETS; i++) {
            assertEquals(expected[i], back.bucketCounts[i], "bucket " + i);
        }
    }

    @Test
    void bucketStartsAreCumulative() {
        SectionDesc desc = SectionDesc.builder(0, 0, 0, 0)
                .bucketCounts(new int[]{10, 0, 5, 3, 0, 0, 2})
                .build();
        assertEquals(0, desc.bucketStartQuad(0));
        assertEquals(10, desc.bucketStartQuad(1));
        assertEquals(10, desc.bucketStartQuad(2));
        assertEquals(15, desc.bucketStartQuad(3));
        assertEquals(18, desc.bucketStartQuad(6));
        assertEquals(20, desc.totalQuads());
        assertEquals(80, desc.totalVertices());
        assertEquals(120, desc.totalIndices());
    }

    @Test
    void oversizeBucketSaturatesAndFlags() {
        SectionDesc desc = SectionDesc.builder(0, 0, 0, 0)
                .bucketCount(3, SectionDesc.MAX_BUCKET_QUADS + 1)
                .build();
        assertTrue(desc.oversize());
        assertEquals(SectionDesc.MAX_BUCKET_QUADS, desc.bucketCounts[3]);
    }

    @Test
    void sortedIndexNoneIsTheDefault() {
        SectionDesc desc = SectionDesc.builder(1, 2, 3, 0).build();
        assertEquals(SectionDesc.SORTED_INDEX_NONE, desc.sortedIndexBase);
        assertFalse(desc.sorted());
    }

    @Test
    void byteBufferPackMatchesWordPack() {
        SectionDesc desc = SectionDesc.builder(7, -8, 9, 1)
                .slab(2, 4096, 1024)
                .bucketCounts(new int[]{3, 1, 4, 1, 5, 9, 2})
                .generation(77)
                .build();
        int[] words = new int[SectionDesc.WORDS];
        desc.pack(words, 0);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(SectionDesc.BYTES);
        desc.pack(buffer);
        buffer.flip();
        for (int i = 0; i < SectionDesc.WORDS; i++) {
            assertEquals(words[i], buffer.getInt(i * 4), "word " + i);
        }
    }
}
