package org.soutalab.celtium.vulkan.lane2;

import org.junit.jupiter.api.Test;

import org.soutalab.celtium.core.state.RenderStateKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Lane2FixedPipelinesTest {

    @Test
    void k5CatalogueSizeIsInsideThePlanBandAndUnderTheCap() {
        // K5 (plan section 5.4): the fixed pipeline total must be <= 64; the plan's band
        // for the precompiled set is 32..48. 13 formats x 3 variants = 39.
        assertEquals(39, Lane2FixedPipelines.COUNT);
        assertTrue(Lane2FixedPipelines.COUNT >= 32);
        assertTrue(Lane2FixedPipelines.COUNT <= 48);
        assertTrue(Lane2FixedPipelines.COUNT <= Lane2FixedPipelines.K5_MAX_PIPELINES);
    }

    @Test
    void everyIndexRoundTrips() {
        boolean[] seen = new boolean[Lane2FixedPipelines.COUNT];
        for (int format = 1; format <= 13; format++) {
            for (int variant = 0; variant < Lane2FixedPipelines.VARIANTS; variant++) {
                int index = Lane2FixedPipelines.indexOf(format, variant);
                assertTrue(index >= 0);
                assertFalse(seen[index]);
                seen[index] = true;
                assertEquals(format, Lane2FixedPipelines.formatOf(index));
                assertEquals(variant, Lane2FixedPipelines.variantOf(index));
            }
        }
        for (boolean flag : seen) {
            assertTrue(flag);
        }
    }

    @Test
    void outsideTheCatalogueIsARefusalNotACreation() {
        assertEquals(-1, Lane2FixedPipelines.indexOf(0, 0));
        assertEquals(-1, Lane2FixedPipelines.indexOf(14, 0));
        assertEquals(-1, Lane2FixedPipelines.indexOf(1, 3));
        assertEquals(-1, Lane2FixedPipelines.indexOf(1, -1));
    }

    @Test
    void variantComesFromTheRegisterKey() {
        RenderStateKey opaque = new RenderStateKey.Builder().build();
        assertEquals(Lane2FixedPipelines.VARIANT_OPAQUE,
                Lane2FixedPipelines.variantFor(opaque));

        RenderStateKey blendWrite = new RenderStateKey.Builder()
                .blendEnabled(true).depthWriteEnabled(true).build();
        assertEquals(Lane2FixedPipelines.VARIANT_BLEND_DEPTH_WRITE,
                Lane2FixedPipelines.variantFor(blendWrite));

        RenderStateKey blendNoWrite = new RenderStateKey.Builder()
                .blendEnabled(true).depthWriteEnabled(false).build();
        assertEquals(Lane2FixedPipelines.VARIANT_BLEND_NO_DEPTH_WRITE,
                Lane2FixedPipelines.variantFor(blendNoWrite));
    }

    @Test
    void programNamesAreUniqueAndStable() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int i = 0; i < Lane2FixedPipelines.COUNT; i++) {
            assertTrue(names.add(Lane2FixedPipelines.programName(i)),
                    "duplicate program name at " + i);
            assertEquals(Lane2FixedPipelines.programName(i), Lane2FixedPipelines.programName(i));
        }
    }
}
