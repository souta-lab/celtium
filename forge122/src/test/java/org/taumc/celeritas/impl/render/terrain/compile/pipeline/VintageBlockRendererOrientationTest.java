package org.taumc.celeritas.impl.render.terrain.compile.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.EnumFacing;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadOrientation;
import org.junit.jupiter.api.Test;

/**
 * The orientation contract, pinned without a world: a quad's split diagonal follows its own
 * vertex brightness pattern, never an earlier quad's. The pre-fix renderer cached the first
 * orientation decided per cull face for the whole block (the reset ran once per block, before
 * the layer loop), so a second quad on the same face whose own pattern required the opposite
 * diagonal was written with the first quad's vertex order and its brightness interpolation
 * broke along the wrong diagonal.
 */
class VintageBlockRendererOrientationTest {

    private static QuadLightData light(float[] br, int[] lm) {
        var light = new QuadLightData();
        System.arraycopy(br, 0, light.br, 0, 4);
        System.arraycopy(lm, 0, light.lm, 0, 4);
        return light;
    }

    @Test
    void orientationFollowsEachQuadsOwnBrightnessPattern() {
        // br[0]+br[2] > br[1]+br[3] -> NORMAL; the reversed pattern -> FLIP.
        var normalLight = light(new float[] { 1.0f, 0.0f, 1.0f, 0.0f }, new int[4]);
        var flipLight = light(new float[] { 0.0f, 1.0f, 0.0f, 1.0f }, new int[4]);

        assertEquals(ModelQuadOrientation.NORMAL,
                VintageBlockRenderer.orientationFor(EnumFacing.UP, normalLight));
        // The regression case: asked second, on the same face, the flipped pattern must
        // still flip - the per-face cache returned the first quad's NORMAL here.
        assertEquals(ModelQuadOrientation.FLIP,
                VintageBlockRenderer.orientationFor(EnumFacing.UP, flipLight));
        // And in the opposite order, for the same reason.
        assertEquals(ModelQuadOrientation.NORMAL,
                VintageBlockRenderer.orientationFor(EnumFacing.UP, normalLight));
    }

    @Test
    void lightmapBreaksBrightnessTies() {
        var tied = new float[] { 0.5f, 0.5f, 0.5f, 0.5f };
        // lm[0]+lm[2] <= lm[1]+lm[3] -> NORMAL; greater -> FLIP.
        assertEquals(ModelQuadOrientation.NORMAL, VintageBlockRenderer.orientationFor(EnumFacing.NORTH,
                light(tied, new int[] { 0, 5, 0, 5 })));
        assertEquals(ModelQuadOrientation.FLIP, VintageBlockRenderer.orientationFor(EnumFacing.NORTH,
                light(tied, new int[] { 5, 0, 5, 0 })));
    }

    @Test
    void unassignedQuadsKeepNormal() {
        var flipLight = light(new float[] { 0.0f, 1.0f, 0.0f, 1.0f }, new int[4]);
        assertEquals(ModelQuadOrientation.NORMAL, VintageBlockRenderer.orientationFor(null, flipLight));
    }
}
